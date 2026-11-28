import { useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { Maximize, Minimize, ShieldCheck, Tv, Square, ArrowLeftRight } from 'lucide-react';
import type { ConnectionState, MirrorFramePacket, NetworkInfo } from '../core/types';
import { CONNECTION_STATE_META } from '../core/connectionStateMachine';
import { loadReceiverName, saveReceiverName } from '../core/persistence';
import { LanTransportClient } from '../services/lanTransport';

interface ReceiverViewProps {
  connectionState: ConnectionState;
  onConnectionStateChange: (next: ConnectionState) => void;
  onChangeMode: () => void;
}

export function ReceiverView({
  connectionState,
  onConnectionStateChange,
  onChangeMode,
}: ReceiverViewProps) {
  const [receiverName, setReceiverName] = useState<string>(() => loadReceiverName());
  const [networkInfo, setNetworkInfo] = useState<NetworkInfo | null>(null);
  const [isMirroring, setIsMirroring] = useState(false);
  const [senderName, setSenderName] = useState<string>('');
  const [incomingFps, setIncomingFps] = useState(0);
  const [incomingResolution, setIncomingResolution] = useState('—');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [wakeLockActive, setWakeLockActive] = useState(false);
  const [hudVisible, setHudVisible] = useState(true);

  const stageRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const transportRef = useRef<LanTransportClient | null>(null);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const frameCountRef = useRef(0);
  const hudTimerRef = useRef<number | null>(null);

  const acquireWakeLock = async () => {
    try {
      if ('wakeLock' in navigator && navigator.wakeLock) {
        wakeLockRef.current = await navigator.wakeLock.request('screen');
        setWakeLockActive(true);
        wakeLockRef.current.addEventListener('release', () => {
          setWakeLockActive(false);
        });
      }
    } catch {
      setWakeLockActive(false);
    }
  };

  const releaseWakeLock = async () => {
    try {
      if (wakeLockRef.current) {
        await wakeLockRef.current.release();
        wakeLockRef.current = null;
      }
    } catch {
      // Ignore
    }
    setWakeLockActive(false);
  };

  const enterFullscreen = async (isAutoTrigger = false) => {
    try {
      if (isAutoTrigger && typeof navigator !== 'undefined' && navigator.userActivation && !navigator.userActivation.isActive) {
        return;
      }
      const target = stageRef.current || document.documentElement;
      if (!document.fullscreenElement && target.requestFullscreen) {
        await target.requestFullscreen();
        setIsFullscreen(true);
      }
    } catch {
      // Fullscreen requires user gesture in some browsers
    }
  };

  const exitFullscreen = async () => {
    try {
      if (document.fullscreenElement && document.exitFullscreen) {
        await document.exitFullscreen();
        setIsFullscreen(false);
      }
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    const onFsChange = () => {
      setIsFullscreen(Boolean(document.fullscreenElement));
    };
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  useEffect(() => {
    fetch('/api/network-info')
      .then((res) => (res.ok ? res.json() : null))
      .then((info: NetworkInfo | null) => {
        if (info) setNetworkInfo(info);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const client = new LanTransportClient('receiver', receiverName, {
      onConnectionStateChange: (next) => {
        onConnectionStateChange(next);
      },
      onReceiverRegistered: (_dev, netInfo) => {
        setNetworkInfo(netInfo);
      },
      onIncomingSession: ({ transmitterName }) => {
        setSenderName(transmitterName);
        setIsMirroring(true);
        setHudVisible(true);
        acquireWakeLock();
        enterFullscreen(true);
      },
      onFrameReceived: (packet: MirrorFramePacket) => {
        frameCountRef.current += 1;
        setIncomingResolution(`${packet.width}×${packet.height}`);
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        const img = new Image();
        img.onload = () => {
          if (canvas.width !== packet.width || canvas.height !== packet.height) {
            canvas.width = packet.width;
            canvas.height = packet.height;
          }
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        };
        img.src = packet.dataUrl;
      },
      onMirrorStopped: () => {
        setIsMirroring(false);
        setIncomingFps(0);
        releaseWakeLock();
        exitFullscreen();
      },
    });

    transportRef.current = client;
    client.connectSocket();

    const fpsTimer = window.setInterval(() => {
      setIncomingFps(frameCountRef.current);
      frameCountRef.current = 0;
    }, 1000);

    return () => {
      window.clearInterval(fpsTimer);
      releaseWakeLock();
      client.disconnect();
      onConnectionStateChange('disconnected');
    };
  }, []);

  const revealHudTemporarily = () => {
    setHudVisible(true);
    if (hudTimerRef.current) window.clearTimeout(hudTimerRef.current);
    hudTimerRef.current = window.setTimeout(() => {
      if (isMirroring) setHudVisible(false);
    }, 3500);
  };

  const handleStopSession = () => {
    transportRef.current?.stopMirroring();
    setIsMirroring(false);
    releaseWakeLock();
    exitFullscreen();
  };

  const handleNameChange = (value: string) => {
    setReceiverName(value);
    saveReceiverName(value);
  };

  const statusMeta = CONNECTION_STATE_META[connectionState];

  if (isMirroring) {
    return (
      <div
        ref={stageRef}
        onMouseMove={revealHudTemporarily}
        onClick={revealHudTemporarily}
        data-testid="receiver-active-stage"
        className="fixed inset-0 z-50 bg-[#05070A] flex items-center justify-center select-none"
      >
        <canvas
          ref={canvasRef}
          data-testid="receiver-canvas"
          className="w-full h-full object-contain"
        />

        {/* Auto-hiding TV Overlay HUD */}
        <div
          className={`fixed top-0 left-0 right-0 p-4 sm:p-6 bg-gradient-to-b from-black/85 via-black/45 to-transparent transition-opacity duration-300 flex flex-wrap items-center justify-between gap-4 ${
            hudVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'
          }`}
        >
          <div className="flex items-center gap-3">
            <div className="h-2.5 w-2.5 rounded-full bg-emerald-400 animate-pulse" />
            <span className="font-display text-lg font-bold text-white">
              {senderName || 'Verici Bağlı'}
            </span>
            <span className="text-slate-400">·</span>
            <span className="font-mono-tabular text-sm text-[#00D4FF]">
              {incomingFps} FPS · {incomingResolution}
            </span>
            {wakeLockActive && (
              <>
                <span className="text-slate-400">·</span>
                <span className="text-xs text-emerald-300 flex items-center gap-1">
                  <ShieldCheck className="h-3.5 w-3.5" />
                  Ekran Uyanık
                </span>
              </>
            )}
          </div>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              data-dpad="true"
              onClick={() => (isFullscreen ? exitFullscreen() : enterFullscreen())}
              className="dpad-focusable flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#141B26]/90 border border-slate-700 text-xs sm:text-sm font-semibold text-white"
            >
              {isFullscreen ? <Minimize className="h-4 w-4" /> : <Maximize className="h-4 w-4" />}
              <span>{isFullscreen ? 'Tam Ekrandan Çık' : 'Tam Ekran'}</span>
            </button>

            <button
              type="button"
              data-dpad="true"
              onClick={handleStopSession}
              className="dpad-focusable flex items-center gap-2 px-3.5 py-2 rounded-xl bg-rose-500 text-white text-xs sm:text-sm font-semibold"
            >
              <Square className="h-4 w-4 fill-current" />
              <span>Yayını Durdur</span>
            </button>

            <button
              type="button"
              data-dpad="true"
              onClick={onChangeMode}
              className="dpad-focusable flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[#141B26]/90 border border-slate-700 text-xs sm:text-sm font-semibold text-slate-100"
            >
              <ArrowLeftRight className="h-4 w-4 text-[#00D4FF]" />
              <span>Mod Değiştir</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <motion.main
      ref={stageRef}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
      className="min-h-[calc(100vh-4rem)] flex flex-col items-center justify-center px-4 sm:px-6 py-10"
    >
      <div className="w-full max-w-4xl rounded-3xl bg-[#11161F] border border-slate-800/90 p-7 sm:p-12 text-center shadow-[0_24px_80px_rgba(0,0,0,0.45)]">
        {/* Pulse Radar Icon */}
        <div className="relative mx-auto mb-8 flex h-24 w-24 items-center justify-center">
          <div className="absolute inset-0 rounded-full border border-[#00D4FF]/35 hiz-pulse-ring" />
          <div className="relative flex h-20 w-20 items-center justify-center rounded-2xl bg-[#161F2D] border border-[#00D4FF]/40 text-[#00D4FF]">
            <Tv className="h-10 w-10" />
          </div>
        </div>

        <h1
          data-testid="receiver-waiting-heading"
          className="font-display text-3xl sm:text-5xl font-extrabold tracking-tight text-white"
        >
          {connectionState === 'reconnecting'
            ? 'Yeniden Bağlanılıyor'
            : connectionState === 'dropped'
              ? 'Bağlantı Koptu — Bekleniyor'
              : 'Bağlantı bekleniyor'}
        </h1>
        <p className="mt-3 text-base sm:text-lg text-slate-300 max-w-xl mx-auto">
          Telefonunuzda Hız Verici Modu&apos;nu açıp bu cihazı seçin veya aşağıdaki yerel ağ adresini girin.
        </p>

        {/* Large TV-Readable IP & Port Display */}
        <div className="mt-9 grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-2xl mx-auto">
          <div className="rounded-2xl bg-[#0A0E15] border border-slate-800 p-6">
            <div className="text-xs sm:text-sm text-slate-400 mb-2">Yerel Ağ IP Adresi</div>
            <div
              data-testid="receiver-lan-ip"
              className="font-mono-tabular text-3xl sm:text-4xl font-bold text-[#00D4FF] tracking-tight"
            >
              {networkInfo?.lanIp ?? '127.0.0.1'}
            </div>
          </div>

          <div className="rounded-2xl bg-[#0A0E15] border border-slate-800 p-6">
            <div className="text-xs sm:text-sm text-slate-400 mb-2">Dinlenen Port</div>
            <div
              data-testid="receiver-port"
              className="font-mono-tabular text-3xl sm:text-4xl font-bold text-white tracking-tight"
            >
              {networkInfo?.port ?? 3000}
            </div>
          </div>
        </div>

        {/* Receiver Name & Controls */}
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <div className="flex items-center gap-2 rounded-xl bg-[#0B0F16] border border-slate-800 focus-within:border-[#00D4FF] focus-within:ring-1 focus-within:ring-[#00D4FF]/60 px-3.5 py-2 transition-colors">
            <label htmlFor="receiver-name-input" className="text-xs text-slate-400 whitespace-nowrap">
              TV Adı:
            </label>
            <input
              id="receiver-name-input"
              data-dpad="true"
              type="text"
              value={receiverName}
              onChange={(e) => handleNameChange(e.target.value)}
              className="bg-transparent text-sm font-semibold text-white w-40 focus:outline-none"
            />
          </div>

          <button
            type="button"
            data-dpad="true"
            onClick={() => (isFullscreen ? exitFullscreen() : enterFullscreen())}
            className="dpad-focusable flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#161F2D] border border-slate-700/80 text-sm font-semibold text-slate-100 hover:border-[#00D4FF]/60"
          >
            {isFullscreen ? <Minimize className="h-4 w-4 text-[#00D4FF]" /> : <Maximize className="h-4 w-4 text-[#00D4FF]" />}
            <span>{isFullscreen ? 'Tam Ekrandan Çık' : 'Tam Ekran Modu'}</span>
          </button>

          <button
            type="button"
            data-dpad="true"
            onClick={acquireWakeLock}
            className={`dpad-focusable flex items-center gap-2 px-4 py-2.5 rounded-xl border text-sm font-semibold ${
              wakeLockActive
                ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300'
                : 'bg-[#161F2D] border-slate-700/80 text-slate-100 hover:border-[#00D4FF]/60'
            }`}
          >
            <ShieldCheck className="h-4 w-4" />
            <span>{wakeLockActive ? 'Ekran Uyanık Tutuluyor' : 'Ekranı Uyanık Tut'}</span>
          </button>
        </div>

        <p className="mt-6 text-xs text-slate-400">
          Durum: {statusMeta.label} · Yayın başladığında tam ekran ve uyanık tutma otomatik etkinleşir.
        </p>
      </div>
    </motion.main>
  );
}
