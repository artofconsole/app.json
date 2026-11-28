import { motion } from 'motion/react';
import { Smartphone, Tv, ArrowRight } from 'lucide-react';
import type { AppMode } from '../core/types';

interface ModeSelectorProps {
  lastSavedMode: AppMode | null;
  onSelectMode: (mode: AppMode) => void;
}

export function ModeSelector({ lastSavedMode, onSelectMode }: ModeSelectorProps) {
  return (
    <motion.main
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
      className="min-h-[calc(100vh-4rem)] flex flex-col justify-center max-w-5xl mx-auto px-4 sm:px-6 py-10"
    >
      <div className="max-w-2xl mb-10">
        <h1 className="font-display text-3xl sm:text-5xl font-bold tracking-tight text-white text-balance">
          Yansıtma Modunu Seçin
        </h1>
        <p className="mt-3 text-base sm:text-lg text-slate-300 leading-relaxed">
          Aynı yerel ağ (LAN) üzerinde cihazınızın ekranını göndermek için Verici Modu&apos;nu,
          büyük ekranda yayını karşılamak için Alıcı Modu&apos;nu başlatın.
        </p>
        {lastSavedMode && (
          <p className="mt-3 text-sm text-slate-400">
            Son seçili mod:{' '}
            <span className="text-[#00D4FF] font-medium">
              {lastSavedMode === 'transmitter' ? 'Verici Modu (Ekran Gönder)' : 'Alıcı Modu (TV Ekranı)'}
            </span>
          </p>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-stretch">
        <button
          type="button"
          data-dpad="true"
          data-testid="select-transmitter-btn"
          autoFocus={lastSavedMode !== 'receiver'}
          onClick={() => onSelectMode('transmitter')}
          className="dpad-focusable group text-left flex flex-col justify-between p-7 sm:p-8 rounded-2xl bg-[#11161F] border border-slate-800 hover:border-[#00D4FF]/60 transition-colors min-h-[260px]"
        >
          <div>
            <div className="flex items-center justify-between gap-4 mb-6">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#172130] border border-[#00D4FF]/30 text-[#00D4FF]">
                <Smartphone className="h-7 w-7" />
              </div>
              {lastSavedMode === 'transmitter' && (
                <span className="text-xs font-medium text-[#00D4FF]">Son Kullanılan</span>
              )}
            </div>

            <h2 className="font-display text-2xl font-bold text-white">
              Verici Modu (Ekran Gönder)
            </h2>
            <p className="mt-2.5 text-sm sm:text-base text-slate-300 leading-relaxed">
              Yerel ağdaki alıcı TV&apos;leri otomatik tarayın veya IP/Port ile bağlanarak ekranınızı
              düşük gecikmeyle aktarın.
            </p>
          </div>

          <div className="mt-8 flex items-center gap-2 text-sm font-semibold text-[#00D4FF]">
            <span>Vericiyi Başlat</span>
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
          </div>
        </button>

        <button
          type="button"
          data-dpad="true"
          data-testid="select-receiver-btn"
          autoFocus={lastSavedMode === 'receiver'}
          onClick={() => onSelectMode('receiver')}
          className="dpad-focusable group text-left flex flex-col justify-between p-7 sm:p-8 rounded-2xl bg-[#11161F] border border-slate-800 hover:border-[#00D4FF]/60 transition-colors min-h-[260px]"
        >
          <div>
            <div className="flex items-center justify-between gap-4 mb-6">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#172130] border border-emerald-400/30 text-emerald-400">
                <Tv className="h-7 w-7" />
              </div>
              {lastSavedMode === 'receiver' && (
                <span className="text-xs font-medium text-[#00D4FF]">Son Kullanılan</span>
              )}
            </div>

            <h2 className="font-display text-2xl font-bold text-white">
              Alıcı Modu (TV Ekranı)
            </h2>
            <p className="mt-2.5 text-sm sm:text-base text-slate-300 leading-relaxed">
              Yerel IP adresinizi ve dinlenen portu TV&apos;de görüntüleyin; yayın başladığında
              tam ekran ve uyanık modda karşılayın.
            </p>
          </div>

          <div className="mt-8 flex items-center gap-2 text-sm font-semibold text-[#00D4FF]">
            <span>Alıcıyı Başlat</span>
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
          </div>
        </button>
      </div>

      <p className="mt-8 text-xs sm:text-sm text-slate-400">
        Kumanda veya klavye ile yön tuşlarını kullanarak kartlar arasında gezinebilir, Enter ile seçim yapabilirsiniz.
      </p>
    </motion.main>
  );
}
