import type { CaptureSourceType, MirrorFramePacket, QualityProfile } from '../core/types';
import { computeScaledDimensions, getFrameIntervalMs } from '../core/presets';

export interface CaptureEngineCallbacks {
  onFrameEncoded: (packet: MirrorFramePacket) => void;
  onCaptureEnded?: (reason: string) => void;
  onPreviewUpdate?: (dataUrl: string) => void;
}

export class ScreenCaptureEngine {
  private stream: MediaStream | null = null;
  private videoEl: HTMLVideoElement | null = null;
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private timerId: number | null = null;
  private running = false;
  private seq = 0;
  private sourceType: CaptureSourceType = 'test_canvas';
  private profile: QualityProfile;
  private callbacks: CaptureEngineCallbacks;
  private photoBitmap: HTMLImageElement | null = null;
  private startTimestamp = performance.now();

  constructor(profile: QualityProfile, callbacks: CaptureEngineCallbacks) {
    this.profile = profile;
    this.callbacks = callbacks;
    this.canvas = document.createElement('canvas');
    const context = this.canvas.getContext('2d', { alpha: false });
    if (!context) {
      throw new Error('2D Canvas bağlamı oluşturulamadı.');
    }
    this.ctx = context;
  }

  public updateProfile(profile: QualityProfile): void {
    this.profile = profile;
    if (this.running) {
      this.restartLoop();
    }
  }

  public setPhotoSource(imageEl: HTMLImageElement): void {
    this.photoBitmap = imageEl;
    this.sourceType = 'media_photo';
  }

  public async start(sourceType: CaptureSourceType): Promise<{ actualSource: CaptureSourceType; warning?: string }> {
    this.stop();
    this.sourceType = sourceType;
    this.startTimestamp = performance.now();

    if (sourceType === 'display') {
      if (typeof navigator !== 'undefined' && navigator.mediaDevices && typeof navigator.mediaDevices.getDisplayMedia === 'function') {
        try {
          const mediaStream = await navigator.mediaDevices.getDisplayMedia({
            video: {
              frameRate: { ideal: this.profile.fps, max: 60 },
              width: { ideal: this.profile.width },
              height: { ideal: this.profile.height },
            },
            audio: false,
          });
          this.stream = mediaStream;
          const video = document.createElement('video');
          video.srcObject = mediaStream;
          video.muted = true;
          video.playsInline = true;
          await video.play();
          this.videoEl = video;

          const [track] = mediaStream.getVideoTracks();
          if (track) {
            track.onended = () => {
              this.stop();
              this.callbacks.onCaptureEnded?.('Ekran paylaşımı tarayıcıdan sonlandırıldı.');
            };
          }

          this.running = true;
          this.restartLoop();
          return { actualSource: 'display' };
        } catch {
          // Graceful fallback to live test stream when permission is denied or headless
          this.sourceType = 'test_canvas';
          this.running = true;
          this.restartLoop();
          return {
            actualSource: 'test_canvas',
            warning: 'Tarayıcı ekran yakalama izni verilmediği için Canlı Test Akışı başlatıldı.',
          };
        }
      } else {
        this.sourceType = 'test_canvas';
        this.running = true;
        this.restartLoop();
        return {
          actualSource: 'test_canvas',
          warning: 'Bu ortam ekran yakalamayı desteklemediği için Canlı Test Akışı kullanılıyor.',
        };
      }
    }

    this.running = true;
    this.restartLoop();
    return { actualSource: this.sourceType };
  }

  private restartLoop(): void {
    if (this.timerId !== null) {
      window.clearInterval(this.timerId);
      this.timerId = null;
    }
    const interval = getFrameIntervalMs(this.profile);
    this.captureTick();
    this.timerId = window.setInterval(() => {
      this.captureTick();
    }, interval);
  }

  private captureTick(): void {
    if (!this.running) return;

    let targetW = this.profile.width;
    let targetH = this.profile.height;

    if (this.sourceType === 'display' && this.videoEl && this.videoEl.videoWidth > 0) {
      const scaled = computeScaledDimensions(
        this.videoEl.videoWidth,
        this.videoEl.videoHeight,
        this.profile.width,
        this.profile.height,
      );
      targetW = scaled.width;
      targetH = scaled.height;
      this.canvas.width = targetW;
      this.canvas.height = targetH;
      this.ctx.drawImage(this.videoEl, 0, 0, targetW, targetH);
    } else if (this.sourceType === 'media_photo' && this.photoBitmap) {
      this.canvas.width = targetW;
      this.canvas.height = targetH;
      this.renderPhotoFrame(targetW, targetH, this.photoBitmap);
    } else {
      this.canvas.width = targetW;
      this.canvas.height = targetH;
      this.renderLiveTestFrame(targetW, targetH);
    }

    const dataUrl = this.canvas.toDataURL('image/jpeg', this.profile.jpegQuality);
    this.seq += 1;
    const packet: MirrorFramePacket = {
      seq: this.seq,
      timestamp: Date.now(),
      width: targetW,
      height: targetH,
      sourceType: this.sourceType,
      dataUrl,
    };

    this.callbacks.onPreviewUpdate?.(dataUrl);
    this.callbacks.onFrameEncoded(packet);
  }

  private renderPhotoFrame(w: number, h: number, img: HTMLImageElement): void {
    this.ctx.fillStyle = '#090C11';
    this.ctx.fillRect(0, 0, w, h);
    const ratio = Math.min(w / img.naturalWidth, h / img.naturalHeight);
    const drawW = img.naturalWidth * ratio;
    const drawH = img.naturalHeight * ratio;
    const x = (w - drawW) / 2;
    const y = (h - drawH) / 2;
    this.ctx.drawImage(img, x, y, drawW, drawH);

    this.ctx.fillStyle = 'rgba(9, 12, 17, 0.72)';
    this.ctx.fillRect(20, h - 52, 270, 34);
    this.ctx.fillStyle = '#00D4FF';
    this.ctx.font = '600 14px "Plus Jakarta Sans", sans-serif';
    this.ctx.fillText('Hız · Medya / Fotoğraf Paylaşımı', 32, h - 30);
  }

  private renderLiveTestFrame(w: number, h: number): void {
    const elapsedSec = (performance.now() - this.startTimestamp) / 1000;
    const grad = this.ctx.createLinearGradient(0, 0, w, h);
    grad.addColorStop(0, '#090D16');
    grad.addColorStop(0.55, '#0E1726');
    grad.addColorStop(1, '#08111E');
    this.ctx.fillStyle = grad;
    this.ctx.fillRect(0, 0, w, h);

    // Subtle calibration grid
    this.ctx.strokeStyle = 'rgba(0, 212, 255, 0.08)';
    this.ctx.lineWidth = 1;
    const step = Math.round(w / 16);
    for (let x = step; x < w; x += step) {
      this.ctx.beginPath();
      this.ctx.moveTo(x, 0);
      this.ctx.lineTo(x, h);
      this.ctx.stroke();
    }
    for (let y = step; y < h; y += step) {
      this.ctx.beginPath();
      this.ctx.moveTo(0, y);
      this.ctx.lineTo(w, y);
      this.ctx.stroke();
    }

    // Animated carrier wave
    this.ctx.strokeStyle = '#00D4FF';
    this.ctx.lineWidth = Math.max(2, Math.round(w / 320));
    this.ctx.beginPath();
    for (let x = 0; x <= w; x += 12) {
      const y =
        h * 0.62 +
        Math.sin(x * 0.014 + elapsedSec * 4.2) * (h * 0.09) +
        Math.cos(x * 0.031 - elapsedSec * 2.5) * (h * 0.035);
      if (x === 0) this.ctx.moveTo(x, y);
      else this.ctx.lineTo(x, y);
    }
    this.ctx.stroke();

    // Moving sync marker bar to visually prove real-time low-latency frame streaming
    const barX = ((elapsedSec * 240) % (w - 140)) + 70;
    this.ctx.fillStyle = '#10B981';
    this.ctx.fillRect(barX, h * 0.78, 44, 8);

    // Crisp brand & live clock
    this.ctx.fillStyle = '#F8FAFC';
    this.ctx.font = `700 ${Math.round(w * 0.055)}px "Outfit", sans-serif`;
    this.ctx.fillText('Hız LAN Yansıtma Aktif', Math.round(w * 0.06), Math.round(h * 0.24));

    this.ctx.fillStyle = '#94A3B8';
    this.ctx.font = `500 ${Math.round(w * 0.024)}px "Plus Jakarta Sans", sans-serif`;
    this.ctx.fillText(
      `Profil: ${this.profile.label} · ${w}x${h} · ${this.profile.fps} FPS hedefi`,
      Math.round(w * 0.06),
      Math.round(h * 0.32),
    );

    const nowStr = new Date().toLocaleTimeString('tr-TR', {
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    const msStr = String(Date.now() % 1000).padStart(3, '0');
    this.ctx.fillStyle = '#00D4FF';
    this.ctx.font = `700 ${Math.round(w * 0.038)}px "JetBrains Mono", monospace`;
    this.ctx.fillText(`${nowStr}.${msStr}  ·  Kare #${this.seq + 1}`, Math.round(w * 0.06), Math.round(h * 0.44));
  }

  public stop(): void {
    this.running = false;
    if (this.timerId !== null) {
      window.clearInterval(this.timerId);
      this.timerId = null;
    }
    if (this.videoEl) {
      this.videoEl.pause();
      this.videoEl.srcObject = null;
      this.videoEl = null;
    }
    if (this.stream) {
      for (const track of this.stream.getTracks()) {
        track.stop();
      }
      this.stream = null;
    }
  }
}
