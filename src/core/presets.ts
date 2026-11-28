import type { QualityPresetId, QualityProfile } from './types';

export const QUALITY_PRESETS: Record<QualityPresetId, QualityProfile> = {
  low_latency: {
    id: 'low_latency',
    label: 'Düşük Gecikme',
    description: 'Oyun, sunum ve anlık etkileşim için minimum tamponlama',
    fps: 45,
    width: 960,
    height: 540,
    jpegQuality: 0.62,
    bitrateKbps: 2800,
    bufferFrames: 1,
    keyframeIntervalSec: 1,
  },
  balanced: {
    id: 'balanced',
    label: 'Dengeli',
    description: 'Günlük ekran yansıtma için akıcı kare hızı ve net görüntü',
    fps: 30,
    width: 1280,
    height: 720,
    jpegQuality: 0.78,
    bitrateKbps: 4800,
    bufferFrames: 2,
    keyframeIntervalSec: 2,
  },
  high_quality: {
    id: 'high_quality',
    label: 'Yüksek Kalite',
    description: 'Video, fotoğraf ve yüksek detay için Full HD akış',
    fps: 24,
    width: 1920,
    height: 1080,
    jpegQuality: 0.9,
    bitrateKbps: 8500,
    bufferFrames: 4,
    keyframeIntervalSec: 2,
  },
};

export const QUALITY_PRESET_LIST: QualityProfile[] = [
  QUALITY_PRESETS.low_latency,
  QUALITY_PRESETS.balanced,
  QUALITY_PRESETS.high_quality,
];

export function getQualityProfile(presetId: QualityPresetId): QualityProfile {
  return QUALITY_PRESETS[presetId] ?? QUALITY_PRESETS.balanced;
}

export function getFrameIntervalMs(profile: QualityProfile): number {
  const safeFps = Math.max(1, Math.min(120, profile.fps));
  return Math.round(1000 / safeFps);
}

export function computeScaledDimensions(
  sourceWidth: number,
  sourceHeight: number,
  maxWidth: number,
  maxHeight: number,
): { width: number; height: number } {
  if (sourceWidth <= 0 || sourceHeight <= 0) {
    return { width: maxWidth, height: maxHeight };
  }
  const ratio = Math.min(maxWidth / sourceWidth, maxHeight / sourceHeight, 1);
  const evenWidth = Math.max(2, Math.floor((sourceWidth * ratio) / 2) * 2);
  const evenHeight = Math.max(2, Math.floor((sourceHeight * ratio) / 2) * 2);
  return { width: evenWidth, height: evenHeight };
}
