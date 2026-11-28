import type { ExtensionModuleInfo, ExtensionModuleStatus } from '../../core/types';

export interface AudioTransportPacket {
  seq: number;
  sampleRate: number;
  channels: number;
  pcmBase64: string;
}

/**
 * Faz 2 Modülü: Yerel Ağ Ses Aktarımı
 * MVP'de ekran akışına odaklanılır; bu modül mikrofon/sistem sesi yakalama
 * ve PCM paketleme arabirimini temiz biçimde hazır tutar.
 */
export class AudioTransportModule {
  private status: ExtensionModuleStatus = 'ready';
  private enabled = false;

  public getInfo(): ExtensionModuleInfo {
    return {
      id: 'audio_transport',
      name: 'Ses Aktarımı',
      phase: 'Faz 2 Modülü',
      status: this.enabled ? 'active' : this.status,
      summary: this.enabled
        ? 'Ses kanalı paketi sonraki sürüm aktarım hattı için etkinleştirildi.'
        : 'Sistem ve mikrofon sesi senkronize PCM/Opus kanalı olarak genişletilmeye hazır.',
    };
  }

  public isEnabled(): boolean {
    return this.enabled;
  }

  public setEnabled(next: boolean): ExtensionModuleInfo {
    this.enabled = next;
    return this.getInfo();
  }
}
