import type { ExtensionModuleInfo } from '../../core/types';

/**
 * Faz 2 Modülü: Seçili Medya / Fotoğraf Paylaşımı
 * Kullanıcının cihazından seçtiği fotoğrafı doğrudan LAN kare hattına besler.
 */
export class MediaShareModule {
  private selectedFileName: string | null = null;

  public getInfo(): ExtensionModuleInfo {
    return {
      id: 'media_share',
      name: 'Fotoğraf & Medya Paylaşımı',
      phase: 'Faz 2 Modülü',
      status: this.selectedFileName ? 'active' : 'ready',
      summary: this.selectedFileName
        ? `Seçili görsel: ${this.selectedFileName}`
        : 'Yerel ağ üzerinden tam çözünürlüklü fotoğraf yansıtmak için görsel seçin.',
    };
  }

  public loadImageFile(file: File): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('Görsel dosyası okunamadı.'));
      reader.onload = () => {
        const img = new Image();
        img.onload = () => {
          this.selectedFileName = file.name;
          resolve(img);
        };
        img.onerror = () => reject(new Error('Geçersiz görsel formatı.'));
        img.src = String(reader.result);
      };
      reader.readAsDataURL(file);
    });
  }

  public clear(): void {
    this.selectedFileName = null;
  }
}
