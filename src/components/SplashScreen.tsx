import { useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { Zap } from 'lucide-react';

interface SplashScreenProps {
  onFinish: () => void;
}

export function SplashScreen({ onFinish }: SplashScreenProps) {
  const [useFallback, setUseFallback] = useState(false);
  const [exiting, setExiting] = useState(false);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const finishedRef = useRef(false);

  const completeSplash = () => {
    if (finishedRef.current) return;
    finishedRef.current = true;
    setExiting(true);
    window.setTimeout(() => {
      onFinish();
    }, 280);
  };

  useEffect(() => {
    // If video cannot start within 900ms (e.g. raw asset absent), activate animated lightning fallback
    const probeTimer = window.setTimeout(() => {
      const video = videoRef.current;
      if (!video || video.readyState < 2 || video.networkState === 3) {
        setUseFallback(true);
      }
    }, 900);

    const handleSkipKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'Escape') {
        e.preventDefault();
        completeSplash();
      }
    };

    window.addEventListener('keydown', handleSkipKey);
    return () => {
      window.clearTimeout(probeTimer);
      window.removeEventListener('keydown', handleSkipKey);
    };
  }, []);

  useEffect(() => {
    if (!useFallback) return;
    const autoAdvanceTimer = window.setTimeout(() => {
      completeSplash();
    }, 2400);
    return () => window.clearTimeout(autoAdvanceTimer);
  }, [useFallback]);

  return (
    <motion.div
      initial={{ opacity: 1 }}
      animate={{ opacity: exiting ? 0 : 1, scale: exiting ? 1.02 : 1 }}
      transition={{ duration: 0.28, ease: [0.16, 1, 0.3, 1] }}
      onClick={completeSplash}
      role="region"
      aria-label="Hız Açılış Ekranı"
      data-testid="splash-screen"
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-[#07090D] cursor-pointer select-none overflow-hidden"
    >
      {!useFallback && (
        <video
          ref={videoRef}
          autoPlay
          muted
          playsInline
          onEnded={completeSplash}
          onError={() => setUseFallback(true)}
          className="absolute inset-0 w-full h-full object-cover"
        >
          <source src="/res/raw/splash_lightning.mp4" type="video/mp4" />
          <source src="/1000059969.mp4" type="video/mp4" />
        </video>
      )}

      {/* Dark anthracite lightning fallback & cinematic overlay */}
      <div
        className={`relative z-10 flex flex-col items-center justify-center text-center px-6 transition-opacity duration-300 ${
          useFallback ? 'opacity-100' : 'opacity-90'
        }`}
      >
        {useFallback && (
          <>
            <div
              aria-hidden="true"
              className="absolute -inset-32 rounded-full bg-[#00D4FF]/10 blur-3xl hiz-lightning-glow pointer-events-none"
            />
            <div className="relative mb-7 flex items-center justify-center">
              <div className="absolute h-28 w-28 rounded-full border border-[#00D4FF]/30 hiz-pulse-ring" />
              <div className="relative flex h-24 w-24 items-center justify-center rounded-2xl bg-[#111722] border border-[#00D4FF]/40 shadow-[0_0_40px_rgba(0,212,255,0.22)]">
                <Zap className="h-12 w-12 text-[#00D4FF] fill-[#00D4FF]/20" strokeWidth={2.2} />
              </div>
            </div>

            <h1 className="font-display text-5xl sm:text-6xl font-extrabold tracking-tight text-white">
              Hız
            </h1>
            <p className="mt-3 text-base sm:text-lg text-slate-300 max-w-md">
              Yerel ağ üzerinden ultra düşük gecikmeli ekran yansıtma
            </p>
          </>
        )}
      </div>

      {/* Skip affordance for touch, mouse, and TV remote */}
      <div className="absolute bottom-8 right-8 z-20">
        <button
          type="button"
          data-dpad="true"
          onClick={(e) => {
            e.stopPropagation();
            completeSplash();
          }}
          className="dpad-focusable px-5 py-2.5 rounded-xl bg-[#141B26]/90 border border-slate-700/80 text-sm font-semibold text-slate-100 hover:border-[#00D4FF]/70 hover:text-white whitespace-nowrap"
        >
          Atla
        </button>
      </div>
    </motion.div>
  );
}
