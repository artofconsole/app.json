import express from 'express';
import http from 'http';
import dgram from 'dgram';
import net from 'net';
import os from 'os';
import path from 'path';
import { fileURLToPath } from 'url';
import { WebSocketServer, WebSocket } from 'ws';
import type { DiscoveredDevice, NetworkInfo } from './src/core/types';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT || 3000);
const UDP_DISCOVERY_PORT = 41234;
const IS_PROD = process.env.NODE_ENV === 'production';

function getLocalNetworkInfo(): NetworkInfo {
  const interfaces = os.networkInterfaces();
  const ips: string[] = [];

  for (const name of Object.keys(interfaces)) {
    const entries = interfaces[name];
    if (!entries) continue;
    for (const iface of entries) {
      if (iface.family === 'IPv4' && !iface.internal) {
        ips.push(iface.address);
      }
    }
  }

  const preferredIp =
    ips.find((ip) => ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.')) ??
    ips[0] ??
    '127.0.0.1';

  return {
    lanIp: preferredIp,
    allIps: ips.length > 0 ? ips : ['127.0.0.1'],
    hostname: os.hostname(),
    port: PORT,
    udpDiscoveryPort: UDP_DISCOVERY_PORT,
  };
}

interface ReceiverEntry {
  id: string;
  name: string;
  ip: string;
  port: number;
  status: 'ready' | 'busy';
  ws: WebSocket;
  pairedTransmitterId: string | null;
  lastSeenAt: number;
}

interface TransmitterEntry {
  id: string;
  name: string;
  ws: WebSocket;
  pairedReceiverId: string | null;
}

const activeReceivers = new Map<string, ReceiverEntry>();
const activeTransmitters = new Map<string, TransmitterEntry>();
const udpDiscoveredDevices = new Map<string, DiscoveredDevice>();

function getCombinedReceivers(): DiscoveredDevice[] {
  const now = Date.now();
  const list: DiscoveredDevice[] = [];
  const seenIds = new Set<string>();

  for (const rx of activeReceivers.values()) {
    seenIds.add(rx.id);
    list.push({
      id: rx.id,
      name: rx.name,
      ip: rx.ip,
      port: rx.port,
      source: 'lan_ws',
      lastSeenAt: rx.lastSeenAt,
      status: rx.status,
    });
  }

  for (const [id, dev] of udpDiscoveredDevices.entries()) {
    if (now - dev.lastSeenAt > 18000) {
      udpDiscoveredDevices.delete(id);
      continue;
    }
    if (!seenIds.has(id)) {
      list.push(dev);
    }
  }

  return list;
}

function safeSend(ws: WebSocket, payload: unknown): void {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function broadcastReceiversToTransmitters(): void {
  const devices = getCombinedReceivers();
  for (const tx of activeTransmitters.values()) {
    safeSend(tx.ws, { type: 'receivers-updated', devices });
  }
}

function setupUdpDiscovery(networkInfo: NetworkInfo): { triggerScan: () => void } {
  const udpSocket = dgram.createSocket({ type: 'udp4', reuseAddr: true });
  let bound = false;

  udpSocket.on('error', (err) => {
    console.warn('[Hız UDP] Discovery socket notice:', err.message);
  });

  udpSocket.on('message', (msg, rinfo) => {
    try {
      const text = msg.toString('utf8');
      const parsed = JSON.parse(text);
      if (parsed.magic !== 'HIZ_LAN_V1') return;

      if (parsed.type === 'HIZ_DISCOVER') {
        for (const rx of activeReceivers.values()) {
          const reply = JSON.stringify({
            magic: 'HIZ_LAN_V1',
            type: 'HIZ_ANNOUNCE',
            device: {
              id: rx.id,
              name: rx.name,
              ip: rx.ip,
              port: rx.port,
              status: rx.status,
            },
          });
          udpSocket.send(reply, rinfo.port, rinfo.address, () => {});
        }
      } else if (parsed.type === 'HIZ_ANNOUNCE' && parsed.device) {
        const dev = parsed.device;
        const devIp = dev.ip || rinfo.address;
        const devId = String(dev.id || `${devIp}:${dev.port || PORT}`);
        if (!activeReceivers.has(devId)) {
          udpDiscoveredDevices.set(devId, {
            id: devId,
            name: String(dev.name || 'Hız TV Alıcısı'),
            ip: devIp,
            port: Number(dev.port || PORT),
            source: 'udp',
            lastSeenAt: Date.now(),
            status: dev.status === 'busy' ? 'busy' : 'ready',
          });
          broadcastReceiversToTransmitters();
        }
      }
    } catch {
      // Ignore unrelated UDP datagrams
    }
  });

  try {
    udpSocket.bind(UDP_DISCOVERY_PORT, () => {
      bound = true;
      try {
        udpSocket.setBroadcast(true);
      } catch {
        // Some containers restrict SO_BROADCAST
      }
    });
  } catch {
    bound = false;
  }

  const triggerScan = () => {
    if (!bound) return;
    const probe = JSON.stringify({
      magic: 'HIZ_LAN_V1',
      type: 'HIZ_DISCOVER',
      fromIp: networkInfo.lanIp,
    });
    try {
      udpSocket.send(probe, UDP_DISCOVERY_PORT, '255.255.255.255', () => {});
    } catch {
      // Ignore broadcast errors in isolated sandboxes
    }
  };

  setInterval(() => {
    if (!bound || activeReceivers.size === 0) return;
    for (const rx of activeReceivers.values()) {
      const announce = JSON.stringify({
        magic: 'HIZ_LAN_V1',
        type: 'HIZ_ANNOUNCE',
        device: {
          id: rx.id,
          name: rx.name,
          ip: rx.ip,
          port: rx.port,
          status: rx.status,
        },
      });
      try {
        udpSocket.send(announce, UDP_DISCOVERY_PORT, '255.255.255.255', () => {});
      } catch {
        // Ignore broadcast errors
      }
    }
  }, 5000);

  return { triggerScan };
}

function probeTcpEndpoint(ip: string, port: number, timeoutMs = 1800): Promise<boolean> {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: ip, port });
    let settled = false;
    const finish = (ok: boolean) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(ok);
    };
    socket.setTimeout(timeoutMs);
    socket.on('connect', () => finish(true));
    socket.on('timeout', () => finish(false));
    socket.on('error', () => finish(false));
  });
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '12mb' }));

  const networkInfo = getLocalNetworkInfo();
  const { triggerScan } = setupUdpDiscovery(networkInfo);

  app.get('/api/network-info', (_req, res) => {
    res.json(getLocalNetworkInfo());
  });

  app.get('/api/devices', (_req, res) => {
    res.json({ devices: getCombinedReceivers() });
  });

  app.post('/api/discovery/scan', (_req, res) => {
    triggerScan();
    res.json({ devices: getCombinedReceivers(), scannedAt: Date.now() });
  });

  app.post('/api/devices/probe', async (req, res) => {
    const { ip, port } = req.body ?? {};
    if (!ip || !port) {
      res.status(400).json({ reachable: false, error: 'IP ve port zorunludur.' });
      return;
    }
    const targetPort = Number(port);
    const localInfo = getLocalNetworkInfo();
    const isSelfHost =
      ip === 'localhost' ||
      ip === '127.0.0.1' ||
      localInfo.allIps.includes(String(ip));

    if (isSelfHost && targetPort === PORT) {
      const localRx = Array.from(activeReceivers.values())[0] ?? null;
      res.json({
        reachable: true,
        hasActiveReceiver: Boolean(localRx),
        receiver: localRx
          ? {
              id: localRx.id,
              name: localRx.name,
              ip: localRx.ip,
              port: localRx.port,
              status: localRx.status,
            }
          : null,
      });
      return;
    }

    const ok = await probeTcpEndpoint(String(ip), targetPort);
    res.json({ reachable: ok, hasActiveReceiver: ok, receiver: null });
  });

  if (!IS_PROD) {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  const httpServer = http.createServer(app);
  const wss = new WebSocketServer({ server: httpServer, path: '/ws' });

  wss.on('connection', (ws) => {
    let clientRole: 'receiver' | 'transmitter' | null = null;
    let clientId: string | null = null;

    ws.on('message', (raw) => {
      let msg: Record<string, any>;
      try {
        msg = JSON.parse(raw.toString('utf8'));
      } catch {
        return;
      }

      switch (msg.type) {
        case 'register-receiver': {
          const info = getLocalNetworkInfo();
          clientRole = 'receiver';
          clientId = String(msg.id || `rx-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`);
          const entry: ReceiverEntry = {
            id: clientId,
            name: String(msg.name || 'Hız TV Alıcısı'),
            ip: info.lanIp,
            port: info.port,
            status: 'ready',
            ws,
            pairedTransmitterId: null,
            lastSeenAt: Date.now(),
          };
          activeReceivers.set(clientId, entry);
          safeSend(ws, {
            type: 'receiver-registered',
            device: {
              id: entry.id,
              name: entry.name,
              ip: entry.ip,
              port: entry.port,
              status: entry.status,
            },
            networkInfo: info,
          });
          broadcastReceiversToTransmitters();
          break;
        }

        case 'register-transmitter': {
          clientRole = 'transmitter';
          clientId = String(msg.id || `tx-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`);
          activeTransmitters.set(clientId, {
            id: clientId,
            name: String(msg.name || 'Hız Telefon Vericisi'),
            ws,
            pairedReceiverId: null,
          });
          safeSend(ws, {
            type: 'receivers-updated',
            devices: getCombinedReceivers(),
          });
          break;
        }

        case 'list-receivers': {
          triggerScan();
          safeSend(ws, {
            type: 'receivers-updated',
            devices: getCombinedReceivers(),
          });
          break;
        }

        case 'connect-request': {
          if (clientRole !== 'transmitter' || !clientId) return;
          const tx = activeTransmitters.get(clientId);
          if (!tx) return;

          const targetId = msg.targetDeviceId ? String(msg.targetDeviceId) : null;
          let targetRx: ReceiverEntry | undefined = targetId
            ? activeReceivers.get(targetId)
            : undefined;

          if (!targetRx && msg.targetIp) {
            const targetIp = String(msg.targetIp);
            const targetPort = Number(msg.targetPort || PORT);
            targetRx = Array.from(activeReceivers.values()).find(
              (rx) =>
                (rx.ip === targetIp || targetIp === 'localhost' || targetIp === '127.0.0.1') &&
                rx.port === targetPort,
            );
          }

          if (!targetRx) {
            safeSend(ws, {
              type: 'connect-error',
              reason: 'Hedef alıcı cihaz yerel ağda bulunamadı veya bekleme modunda değil.',
            });
            return;
          }

          // Unpair previous if any
          if (tx.pairedReceiverId && tx.pairedReceiverId !== targetRx.id) {
            const prevRx = activeReceivers.get(tx.pairedReceiverId);
            if (prevRx) {
              prevRx.pairedTransmitterId = null;
              prevRx.status = 'ready';
              safeSend(prevRx.ws, { type: 'mirror-stopped', reason: 'Verici başka cihaza geçti.' });
            }
          }

          tx.pairedReceiverId = targetRx.id;
          targetRx.pairedTransmitterId = tx.id;
          targetRx.status = 'busy';

          safeSend(ws, {
            type: 'connect-ack',
            receiver: {
              id: targetRx.id,
              name: targetRx.name,
              ip: targetRx.ip,
              port: targetRx.port,
            },
            presetId: msg.presetId,
          });

          safeSend(targetRx.ws, {
            type: 'incoming-mirror-session',
            transmitterId: tx.id,
            transmitterName: tx.name,
            presetId: msg.presetId,
          });

          broadcastReceiversToTransmitters();
          break;
        }

        case 'frame-data': {
          if (clientRole !== 'transmitter' || !clientId) return;
          const tx = activeTransmitters.get(clientId);
          if (!tx || !tx.pairedReceiverId) return;
          const rx = activeReceivers.get(tx.pairedReceiverId);
          if (!rx) {
            tx.pairedReceiverId = null;
            safeSend(ws, {
              type: 'peer-dropped',
              reason: 'Alıcı TV bağlantısı koptu.',
            });
            return;
          }
          safeSend(rx.ws, {
            type: 'frame-data',
            packet: msg.packet,
          });
          break;
        }

        case 'stop-mirroring': {
          if (clientRole === 'transmitter' && clientId) {
            const tx = activeTransmitters.get(clientId);
            if (tx?.pairedReceiverId) {
              const rx = activeReceivers.get(tx.pairedReceiverId);
              if (rx) {
                rx.pairedTransmitterId = null;
                rx.status = 'ready';
                safeSend(rx.ws, { type: 'mirror-stopped', reason: 'Yansıtma verici tarafından durduruldu.' });
              }
              tx.pairedReceiverId = null;
            }
            safeSend(ws, { type: 'mirror-stopped', reason: 'Yansıtma durduruldu.' });
            broadcastReceiversToTransmitters();
          } else if (clientRole === 'receiver' && clientId) {
            const rx = activeReceivers.get(clientId);
            if (rx?.pairedTransmitterId) {
              const tx = activeTransmitters.get(rx.pairedTransmitterId);
              if (tx) {
                tx.pairedReceiverId = null;
                safeSend(tx.ws, { type: 'mirror-stopped', reason: 'Alıcı TV yayını sonlandırdı.' });
              }
              rx.pairedTransmitterId = null;
              rx.status = 'ready';
            }
            broadcastReceiversToTransmitters();
          }
          break;
        }

        case 'heartbeat-ping': {
          safeSend(ws, {
            type: 'heartbeat-pong',
            clientTs: msg.clientTs,
            serverTs: Date.now(),
          });
          if (clientRole === 'receiver' && clientId) {
            const rx = activeReceivers.get(clientId);
            if (rx) rx.lastSeenAt = Date.now();
          }
          break;
        }
      }
    });

    ws.on('close', () => {
      if (clientRole === 'receiver' && clientId) {
        const rx = activeReceivers.get(clientId);
        if (rx?.pairedTransmitterId) {
          const tx = activeTransmitters.get(rx.pairedTransmitterId);
          if (tx) {
            tx.pairedReceiverId = null;
            safeSend(tx.ws, {
              type: 'peer-dropped',
              reason: 'Alıcı TV ile soket bağlantısı koptu.',
            });
          }
        }
        activeReceivers.delete(clientId);
        broadcastReceiversToTransmitters();
      } else if (clientRole === 'transmitter' && clientId) {
        const tx = activeTransmitters.get(clientId);
        if (tx?.pairedReceiverId) {
          const rx = activeReceivers.get(tx.pairedReceiverId);
          if (rx) {
            rx.pairedTransmitterId = null;
            rx.status = 'ready';
            safeSend(rx.ws, {
              type: 'peer-dropped',
              reason: 'Verici cihaz bağlantısı kesildi.',
            });
          }
        }
        activeTransmitters.delete(clientId);
        broadcastReceiversToTransmitters();
      }
    });
  });

  httpServer.listen(PORT, '0.0.0.0', () => {
    console.log(`[Hız] Server running on http://${networkInfo.lanIp}:${PORT} (0.0.0.0:${PORT})`);
  });
}

startServer();
