import type {
  ConnectionState,
  DiscoveredDevice,
  MirrorFramePacket,
  NetworkInfo,
  QualityPresetId,
} from '../core/types';
import { getReconnectDelayMs, transitionConnectionState } from '../core/connectionStateMachine';

export interface LanTransportCallbacks {
  onConnectionStateChange?: (state: ConnectionState, message?: string) => void;
  onDevicesUpdated?: (devices: DiscoveredDevice[]) => void;
  onLatencySample?: (latencyMs: number) => void;
  onIncomingSession?: (info: { transmitterId: string; transmitterName: string; presetId?: QualityPresetId }) => void;
  onFrameReceived?: (packet: MirrorFramePacket) => void;
  onMirrorStopped?: (reason: string) => void;
  onReceiverRegistered?: (device: { id: string; name: string; ip: string; port: number }, networkInfo: NetworkInfo) => void;
  onConnectAck?: (receiver: { id: string; name: string; ip: string; port: number }) => void;
  onConnectError?: (reason: string) => void;
}

export class LanTransportClient {
  private ws: WebSocket | null = null;
  private role: 'transmitter' | 'receiver';
  private deviceName: string;
  private deviceId: string;
  private callbacks: LanTransportCallbacks;
  private connectionState: ConnectionState = 'disconnected';
  private reconnectAttempts = 0;
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;
  private manualClose = false;
  private targetEndpointUrl: string;
  private lastPairedTarget: {
    targetDeviceId?: string;
    targetIp?: string;
    targetPort?: number;
    presetId?: QualityPresetId;
  } | null = null;

  constructor(
    role: 'transmitter' | 'receiver',
    deviceName: string,
    callbacks: LanTransportCallbacks,
  ) {
    this.role = role;
    this.deviceName = deviceName;
    this.deviceId = `${role === 'receiver' ? 'rx' : 'tx'}-${Math.random().toString(36).slice(2, 8)}`;
    this.callbacks = callbacks;
    this.targetEndpointUrl = this.buildDefaultWsUrl();
  }

  private buildDefaultWsUrl(): string {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${window.location.host}/ws`;
  }

  private setState(next: ConnectionState, message?: string): void {
    this.connectionState = next;
    this.callbacks.onConnectionStateChange?.(next, message);
  }

  public connectSocket(customIp?: string, customPort?: number): void {
    this.manualClose = false;
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (customIp && customPort) {
      const isCurrentHost =
        customIp === window.location.hostname ||
        customIp === 'localhost' ||
        customIp === '127.0.0.1';
      if (isCurrentHost && String(customPort) === (window.location.port || (window.location.protocol === 'https:' ? '443' : '80'))) {
        this.targetEndpointUrl = this.buildDefaultWsUrl();
      } else {
        const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        this.targetEndpointUrl = `${proto}//${customIp}:${customPort}/ws`;
      }
    } else {
      this.targetEndpointUrl = this.buildDefaultWsUrl();
    }

    this.openWebSocket();
  }

  private openWebSocket(): void {
    if (this.ws) {
      try {
        this.ws.onclose = null;
        this.ws.close();
      } catch {
        // Ignore cleanup errors
      }
      this.ws = null;
    }

    if (this.role === 'receiver') {
      this.setState(transitionConnectionState(this.connectionState, { type: 'CONNECT_START' }), 'Alıcı soketi hazırlanıyor');
    }

    try {
      this.ws = new WebSocket(this.targetEndpointUrl);
    } catch {
      this.handleUnexpectedDrop('Soket adresi açılamadı.');
      return;
    }

    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
      if (this.role === 'receiver') {
        this.sendJson({
          type: 'register-receiver',
          id: this.deviceId,
          name: this.deviceName,
        });
        this.setState('disconnected', 'Bağlantı bekleniyor');
      } else {
        this.sendJson({
          type: 'register-transmitter',
          id: this.deviceId,
          name: this.deviceName,
        });
        if (this.lastPairedTarget && this.connectionState === 'reconnecting') {
          this.pairWithReceiver(this.lastPairedTarget);
        }
      }
      this.startHeartbeat();
    };

    this.ws.onmessage = (event) => {
      let msg: Record<string, any>;
      try {
        msg = JSON.parse(String(event.data));
      } catch {
        return;
      }

      switch (msg.type) {
        case 'receiver-registered':
          if (msg.device && msg.networkInfo) {
            this.callbacks.onReceiverRegistered?.(msg.device, msg.networkInfo);
          }
          break;
        case 'receivers-updated':
          this.callbacks.onDevicesUpdated?.(Array.isArray(msg.devices) ? msg.devices : []);
          break;
        case 'connect-ack':
          this.setState('connected', `${msg.receiver?.name ?? 'Alıcı'} ile eşleşildi.`);
          if (msg.receiver) {
            this.callbacks.onConnectAck?.(msg.receiver);
          }
          break;
        case 'connect-error':
          this.setState('disconnected', msg.reason || 'Bağlantı kurulamadı.');
          this.callbacks.onConnectError?.(msg.reason || 'Hedef alıcı bulunamadı.');
          break;
        case 'incoming-mirror-session':
          this.setState('connected', `${msg.transmitterName || 'Verici'} bağlandı.`);
          this.callbacks.onIncomingSession?.({
            transmitterId: String(msg.transmitterId),
            transmitterName: String(msg.transmitterName || 'Hız Telefon Vericisi'),
            presetId: msg.presetId,
          });
          break;
        case 'frame-data':
          if (msg.packet) {
            this.callbacks.onFrameReceived?.(msg.packet as MirrorFramePacket);
          }
          break;
        case 'mirror-stopped':
          this.lastPairedTarget = null;
          this.setState('disconnected', msg.reason || 'Yansıtma sonlandırıldı.');
          this.callbacks.onMirrorStopped?.(msg.reason || 'Yansıtma sonlandırıldı.');
          break;
        case 'peer-dropped':
          this.handlePeerDropped(msg.reason || 'Karşı cihaz bağlantısı koptu.');
          break;
        case 'heartbeat-pong':
          if (typeof msg.clientTs === 'number') {
            const rtt = Math.max(1, Math.round(performance.now() - msg.clientTs));
            this.callbacks.onLatencySample?.(rtt);
          }
          break;
      }
    };

    this.ws.onerror = () => {
      // onclose handles state transition cleanly
    };

    this.ws.onclose = () => {
      this.stopHeartbeat();
      if (!this.manualClose) {
        this.handleUnexpectedDrop('Yerel ağ soket bağlantısı kapandı.');
      }
    };
  }

  private handlePeerDropped(reason: string): void {
    this.setState('dropped', reason);
    this.callbacks.onMirrorStopped?.(reason);
    if (this.role === 'transmitter' && this.lastPairedTarget) {
      window.setTimeout(() => {
        if (!this.manualClose && this.connectionState === 'dropped') {
          this.setState('reconnecting', 'Hedef TV yeniden aranıyor...');
          this.pairWithReceiver(this.lastPairedTarget!);
        }
      }, 1500);
    }
  }

  private handleUnexpectedDrop(reason: string): void {
    const wasActiveOrConnecting =
      this.connectionState === 'connected' ||
      this.connectionState === 'connecting' ||
      this.connectionState === 'reconnecting';

    if (!wasActiveOrConnecting && this.role === 'transmitter') {
      this.setState('dropped', reason);
    } else {
      this.setState('dropped', reason);
    }

    const delay = getReconnectDelayMs(this.reconnectAttempts);
    this.reconnectAttempts += 1;

    this.reconnectTimer = window.setTimeout(() => {
      if (this.manualClose) return;
      this.setState('reconnecting', `Yeniden bağlanılıyor (deneme ${this.reconnectAttempts})...`);
      this.openWebSocket();
    }, delay);
  }

  public requestDiscoveryList(): void {
    this.sendJson({ type: 'list-receivers' });
  }

  public pairWithReceiver(params: {
    targetDeviceId?: string;
    targetIp?: string;
    targetPort?: number;
    presetId?: QualityPresetId;
  }): void {
    this.lastPairedTarget = params;
    this.setState('connecting', 'Alıcı cihaza bağlanılıyor...');
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      this.openWebSocket();
      window.setTimeout(() => {
        this.sendJson({ type: 'connect-request', ...params });
      }, 250);
      return;
    }
    this.sendJson({ type: 'connect-request', ...params });
  }

  public sendFrame(packet: MirrorFramePacket): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false;
    // Backpressure guard so slow LAN links drop stale frames smoothly instead of queuing
    if (this.ws.bufferedAmount > 1_200_000) {
      return false;
    }
    this.sendJson({ type: 'frame-data', packet });
    return true;
  }

  public stopMirroring(): void {
    this.lastPairedTarget = null;
    this.sendJson({ type: 'stop-mirroring' });
    this.setState('disconnected', 'Yansıtma durduruldu.');
  }

  public disconnect(): void {
    this.manualClose = true;
    this.lastPairedTarget = null;
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stopHeartbeat();
    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // Ignore
      }
      this.ws = null;
    }
    this.setState('disconnected', 'Bağlantı kapatıldı.');
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      this.sendJson({ type: 'heartbeat-ping', clientTs: performance.now() });
    }, 2000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private sendJson(payload: unknown): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(payload));
    }
  }
}
