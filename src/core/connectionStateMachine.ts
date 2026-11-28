import type { ConnectionState } from './types';

export interface ConnectionStatusMeta {
  state: ConnectionState;
  label: string;
  description: string;
  tone: 'neutral' | 'amber' | 'green' | 'red';
  isAnimated: boolean;
}

export const CONNECTION_STATE_META: Record<ConnectionState, ConnectionStatusMeta> = {
  disconnected: {
    state: 'disconnected',
    label: 'Bağlı Değil',
    description: 'Yerel ağda hedef cihaz seçin veya IP/Port girin.',
    tone: 'neutral',
    isAnimated: false,
  },
  connecting: {
    state: 'connecting',
    label: 'Bağlanıyor',
    description: 'Yerel ağ soket el sıkışması kuruluyor.',
    tone: 'amber',
    isAnimated: true,
  },
  connected: {
    state: 'connected',
    label: 'Bağlandı',
    description: 'Yerel ağ bağlantısı etkin ve kararlı.',
    tone: 'green',
    isAnimated: true,
  },
  dropped: {
    state: 'dropped',
    label: 'Bağlantı Koptu',
    description: 'Hedef cihaz ile ağ bağlantısı kesildi.',
    tone: 'red',
    isAnimated: false,
  },
  reconnecting: {
    state: 'reconnecting',
    label: 'Yeniden Bağlanılıyor',
    description: 'Otomatik yeniden bağlanma denemesi yürütülüyor.',
    tone: 'amber',
    isAnimated: true,
  },
};

export type ConnectionEvent =
  | { type: 'CONNECT_START' }
  | { type: 'CONNECT_SUCCESS' }
  | { type: 'CONNECTION_LOST' }
  | { type: 'RECONNECT_ATTEMPT' }
  | { type: 'DISCONNECT' };

export function transitionConnectionState(
  current: ConnectionState,
  event: ConnectionEvent,
): ConnectionState {
  switch (event.type) {
    case 'CONNECT_START':
      return 'connecting';
    case 'CONNECT_SUCCESS':
      return 'connected';
    case 'CONNECTION_LOST':
      return current === 'disconnected' ? 'disconnected' : 'dropped';
    case 'RECONNECT_ATTEMPT':
      return current === 'disconnected' ? 'disconnected' : 'reconnecting';
    case 'DISCONNECT':
      return 'disconnected';
    default:
      return current;
  }
}

export function getReconnectDelayMs(attempt: number): number {
  const clampedAttempt = Math.max(0, Math.min(attempt, 5));
  return Math.min(10000, 1000 * Math.pow(2, clampedAttempt));
}

export interface EndpointValidationResult {
  valid: boolean;
  ip?: string;
  port?: number;
  error?: string;
}

export function validateManualEndpoint(
  rawIp: string,
  rawPort: string | number,
): EndpointValidationResult {
  const ip = rawIp.trim();
  if (!ip) {
    return { valid: false, error: 'Lütfen bir IPv4 adresi girin (ör. 192.168.1.42).' };
  }

  const ipv4Regex = /^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
  const isLocalhost = ip === 'localhost';
  if (!ipv4Regex.test(ip) && !isLocalhost) {
    return { valid: false, error: 'Geçersiz IP adresi formatı. Örnek: 192.168.1.42' };
  }

  const portStr = String(rawPort).trim();
  if (!/^\d+$/.test(portStr)) {
    return { valid: false, error: 'Port numarası yalnızca rakamlardan oluşmalıdır.' };
  }

  const port = Number(portStr);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    return { valid: false, error: 'Port numarası 1 ile 65535 arasında olmalıdır.' };
  }

  return { valid: true, ip, port };
}
