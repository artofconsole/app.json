import type { AppMode, QualityPresetId } from './types';

export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

const KEYS = {
  lastMode: 'hiz:last_mode',
  qualityPreset: 'hiz:quality_preset',
  manualIp: 'hiz:manual_ip',
  manualPort: 'hiz:manual_port',
  receiverName: 'hiz:receiver_name',
} as const;

function getSafeStorage(customStorage?: StorageLike): StorageLike | null {
  if (customStorage) return customStorage;
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      return window.localStorage;
    }
  } catch {
    return null;
  }
  return null;
}

export function saveLastMode(mode: AppMode, storage?: StorageLike): void {
  const s = getSafeStorage(storage);
  if (!s) return;
  try {
    s.setItem(KEYS.lastMode, mode);
  } catch {
    // Storage quota or privacy mode fallback
  }
}

export function loadLastMode(storage?: StorageLike): AppMode | null {
  const s = getSafeStorage(storage);
  if (!s) return null;
  try {
    const val = s.getItem(KEYS.lastMode);
    if (val === 'transmitter' || val === 'receiver') {
      return val;
    }
    return null;
  } catch {
    return null;
  }
}

export function saveQualityPreset(preset: QualityPresetId, storage?: StorageLike): void {
  const s = getSafeStorage(storage);
  if (!s) return;
  try {
    s.setItem(KEYS.qualityPreset, preset);
  } catch {
    // Ignore storage write errors
  }
}

export function loadQualityPreset(storage?: StorageLike): QualityPresetId {
  const s = getSafeStorage(storage);
  if (!s) return 'balanced';
  try {
    const val = s.getItem(KEYS.qualityPreset);
    if (val === 'low_latency' || val === 'balanced' || val === 'high_quality') {
      return val;
    }
    return 'balanced';
  } catch {
    return 'balanced';
  }
}

export function saveManualEndpoint(ip: string, port: string, storage?: StorageLike): void {
  const s = getSafeStorage(storage);
  if (!s) return;
  try {
    s.setItem(KEYS.manualIp, ip);
    s.setItem(KEYS.manualPort, port);
  } catch {
    // Ignore
  }
}

export function loadManualEndpoint(storage?: StorageLike): { ip: string; port: string } {
  const s = getSafeStorage(storage);
  if (!s) return { ip: '', port: '3000' };
  try {
    return {
      ip: s.getItem(KEYS.manualIp) ?? '',
      port: s.getItem(KEYS.manualPort) ?? '3000',
    };
  } catch {
    return { ip: '', port: '3000' };
  }
}

export function loadReceiverName(storage?: StorageLike): string {
  const s = getSafeStorage(storage);
  if (!s) return 'Hız TV Alıcısı';
  try {
    return s.getItem(KEYS.receiverName) || 'Hız TV Alıcısı';
  } catch {
    return 'Hız TV Alıcısı';
  }
}

export function saveReceiverName(name: string, storage?: StorageLike): void {
  const s = getSafeStorage(storage);
  if (!s) return;
  try {
    s.setItem(KEYS.receiverName, name.trim() || 'Hız TV Alıcısı');
  } catch {
    // Ignore
  }
}
