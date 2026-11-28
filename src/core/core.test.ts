import test from 'node:test';
import assert from 'node:assert/strict';
import {
  computeScaledDimensions,
  getFrameIntervalMs,
  getQualityProfile,
} from './presets';
import {
  CONNECTION_STATE_META,
  getReconnectDelayMs,
  transitionConnectionState,
  validateManualEndpoint,
} from './connectionStateMachine';
import {
  loadLastMode,
  loadManualEndpoint,
  loadQualityPreset,
  saveLastMode,
  saveManualEndpoint,
  saveQualityPreset,
  type StorageLike,
} from './persistence';

function createMemoryStorage(): StorageLike {
  const map = new Map<string, string>();
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => {
      map.set(k, v);
    },
    removeItem: (k) => {
      map.delete(k);
    },
  };
}

test('quality presets map to distinct fps, resolution, bitrate, and buffer params', () => {
  const low = getQualityProfile('low_latency');
  const balanced = getQualityProfile('balanced');
  const high = getQualityProfile('high_quality');

  assert.equal(low.label, 'Düşük Gecikme');
  assert.ok(low.fps > balanced.fps);
  assert.ok(low.bufferFrames < balanced.bufferFrames);
  assert.ok(high.width > balanced.width);
  assert.ok(high.bitrateKbps > balanced.bitrateKbps);
  assert.equal(getFrameIntervalMs(balanced), Math.round(1000 / 30));
});

test('computeScaledDimensions preserves aspect ratio with even pixel dimensions', () => {
  const scaled = computeScaledDimensions(2560, 1440, 1280, 720);
  assert.deepEqual(scaled, { width: 1280, height: 720 });

  const portrait = computeScaledDimensions(1080, 1920, 1280, 720);
  assert.equal(portrait.height, 720);
  assert.equal(portrait.width % 2, 0);
  assert.ok(portrait.width < 720);
});

test('connection state machine handles all 5 states cleanly', () => {
  let state = transitionConnectionState('disconnected', { type: 'CONNECT_START' });
  assert.equal(state, 'connecting');
  assert.equal(CONNECTION_STATE_META[state].label, 'Bağlanıyor');

  state = transitionConnectionState(state, { type: 'CONNECT_SUCCESS' });
  assert.equal(state, 'connected');
  assert.equal(CONNECTION_STATE_META[state].label, 'Bağlandı');

  state = transitionConnectionState(state, { type: 'CONNECTION_LOST' });
  assert.equal(state, 'dropped');
  assert.equal(CONNECTION_STATE_META[state].label, 'Bağlantı Koptu');

  state = transitionConnectionState(state, { type: 'RECONNECT_ATTEMPT' });
  assert.equal(state, 'reconnecting');
  assert.equal(CONNECTION_STATE_META[state].label, 'Yeniden Bağlanılıyor');

  state = transitionConnectionState(state, { type: 'DISCONNECT' });
  assert.equal(state, 'disconnected');
  assert.equal(CONNECTION_STATE_META[state].label, 'Bağlı Değil');
});

test('manual IP and port validation rejects malformed inputs and accepts valid LAN endpoints', () => {
  assert.equal(validateManualEndpoint('', '3000').valid, false);
  assert.equal(validateManualEndpoint('999.168.1.1', '3000').valid, false);
  assert.equal(validateManualEndpoint('192.168.1.50', '0').valid, false);
  assert.equal(validateManualEndpoint('192.168.1.50', '70000').valid, false);
  assert.equal(validateManualEndpoint('192.168.1.50', 'abc').valid, false);

  const validLan = validateManualEndpoint(' 192.168.1.42 ', '3000');
  assert.equal(validLan.valid, true);
  assert.equal(validLan.ip, '192.168.1.42');
  assert.equal(validLan.port, 3000);

  const validLocal = validateManualEndpoint('localhost', 3000);
  assert.equal(validLocal.valid, true);
});

test('reconnect backoff scales exponentially up to 10s cap', () => {
  assert.equal(getReconnectDelayMs(0), 1000);
  assert.equal(getReconnectDelayMs(1), 2000);
  assert.equal(getReconnectDelayMs(2), 4000);
  assert.equal(getReconnectDelayMs(5), 10000);
});

test('persistence stores and restores last mode, quality preset, and manual endpoint', () => {
  const storage = createMemoryStorage();
  assert.equal(loadLastMode(storage), null);

  saveLastMode('receiver', storage);
  assert.equal(loadLastMode(storage), 'receiver');

  saveQualityPreset('high_quality', storage);
  assert.equal(loadQualityPreset(storage), 'high_quality');

  saveManualEndpoint('192.168.1.88', '4545', storage);
  assert.deepEqual(loadManualEndpoint(storage), { ip: '192.168.1.88', port: '4545' });
});
