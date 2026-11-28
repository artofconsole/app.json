export type AppMode = 'transmitter' | 'receiver';

export type ConnectionState =
  | 'disconnected'
  | 'connecting'
  | 'connected'
  | 'dropped'
  | 'reconnecting';

export type QualityPresetId = 'low_latency' | 'balanced' | 'high_quality';

export type CaptureSourceType = 'display' | 'test_canvas' | 'media_photo';

export interface QualityProfile {
  id: QualityPresetId;
  label: string;
  description: string;
  fps: number;
  width: number;
  height: number;
  jpegQuality: number;
  bitrateKbps: number;
  bufferFrames: number;
  keyframeIntervalSec: number;
}

export interface DiscoveredDevice {
  id: string;
  name: string;
  ip: string;
  port: number;
  source: 'udp' | 'lan_ws' | 'manual';
  lastSeenAt: number;
  status: 'ready' | 'busy';
}

export interface NetworkInfo {
  lanIp: string;
  allIps: string[];
  hostname: string;
  port: number;
  udpDiscoveryPort: number;
}

export interface StreamMetrics {
  fps: number;
  estimatedLatencyMs: number;
  resolutionLabel: string;
  bitrateKbps: number;
  droppedFrames: number;
  framesTransferred: number;
  activeTargetLabel: string;
}

export interface MirrorFramePacket {
  seq: number;
  timestamp: number;
  width: number;
  height: number;
  sourceType: CaptureSourceType;
  dataUrl: string;
}

export type ExtensionModuleStatus = 'standby' | 'ready' | 'active' | 'unsupported';

export interface ExtensionModuleInfo {
  id: 'audio_transport' | 'media_share';
  name: string;
  phase: 'Faz 2 Modülü';
  status: ExtensionModuleStatus;
  summary: string;
}
