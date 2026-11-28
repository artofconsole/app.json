import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'motion/react';
import {
  Play,
  Square,
  Search,
  Tv,
  Monitor,
  Activity,
  Image as ImageIcon,
  Volume2,
  Sliders,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';
import type {
  CaptureSourceType,
  ConnectionState,
  DiscoveredDevice,
  ExtensionModuleInfo,
  NetworkInfo,
  QualityPresetId,
  StreamMetrics,
} from '../core/types';
import { QUALITY_PRESET_LIST, getQualityProfile } from '../core/presets';
import {
  CONNECTION_STATE_META,
  validateManualEndpoint,
} from '../core/connectionStateMachine';
import {
  loadManualEndpoint,
  loadQualityPreset,
  saveManualEndpoint,
  saveQualityPreset,
} from '../core/persistence';
import { LanTransportClient } from '../services/lanTransport';
import { ScreenCaptureEngine } from '../services/screenCapture';
import { AudioTransportModule } from '../services/modules/audioTransportModule';
import { MediaShareModule } from '../services/modules/mediaShareModule';

interface TransmitterViewProps {
  connectionState: ConnectionState;
  onConnectionStateChange: (next: ConnectionState) => void;
}

export function TransmitterView({
  connectionState,
  onConnectionStateChange,
}: TransmitterViewProps) {
  const initialEndpoint = useMemo(() => loadManualEndpoint(), []);
  const [presetId, setPresetId] = useState<QualityPresetId>(() => loadQualityPreset());
  const [devices, setDevices] = useState<DiscoveredDevice[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | null>(null);
  const [manualIp, setManualIp] = useState<string>(initialEndpoint.ip);
  const [manualPort, setManualPort] = useState<string>(initialEndpoint.port);
  const [endpointError, setEndpointError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string>('Yerel ağda alıcı TV aramak için tarayın veya IP girin.');
  const [isScanning, setIsScanning] = useState(false);
  const [networkInfo, setNetworkInfo] = useState<NetworkInfo | null>(null);

  const [sourceType, setSourceType] = useState<CaptureSourceType>('display');
  const [isStreaming, setIsStreaming] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [activeTarget, setActiveTarget] = useState<{ name: string; ip: string; port: number } | null>(null);
  const [metrics, setMetrics] = useState<StreamMetrics>({
    fps: 0,
    estimatedLatencyMs: 0,
    resolutionLabel: '1280×720',
    bitrateKbps: 4800,
    droppedFrames: 0,
    framesTransferred: 0,
    activeTargetLabel: 'Hedef Seçilmedi',
  });

  const audioModuleRef = useRef(new AudioTransportModule());
  const mediaModuleRef = useRef(new MediaShareModule());
  const [audioModuleInfo, setAudioModuleInfo] = useState<ExtensionModuleInfo>(() =>
    audioModuleRef.current.getInfo(),
  );
  const [mediaModuleInfo, setMediaModuleInfo] = useState<ExtensionModuleInfo>(() =>
    mediaModuleRef.current.getInfo(),
  );

  const transportRef = useRef<LanTransportClient | null>(null);
  const captureRef = useRef<ScreenCaptureEngine | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const frameCounterRef = useRef(0);
  const droppedCounterRef = useRef(0);
  const totalSentRef = useRef(0);
  const activeProfile = useMemo(() => getQualityProfile(presetId), [presetId]);

  useEffect(() => {
    fetch('/api/network-info')
      .then((res) => (res.ok ? res.json() : null))
      .then((info: NetworkInfo | null) => {
        if (info) {
          setNetworkInfo(info);
          setManualIp((prev) => prev || info.lanIp);
        }
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const engine = new ScreenCaptureEngine(activeProfile, {
      onPreviewUpdate: (url) => {
        setPreviewUrl(url);
      },
      onFrameEncoded: (packet) => {
        const client = transportRef.current;
        if (!client) return;
        const ok = client.sendFrame(packet);
        if (ok) {
          frameCounterRef.current += 1;
          totalSentRef.current += 1;
        } else {
          droppedCounterRef.current += 1;
        }
      },
      onCaptureEnded: (reason) => {
        setIsStreaming(false);
        setStatusMessage(reason);
        transportRef.current?.stopMirroring();
      },
    });
    captureRef.current = engine;

    const client = new LanTransportClient('transmitter', 'Hız Verici (Telefon)', {
      onConnectionStateChange: (next, msg) => {
        onConnectionStateChange(next);
        if (msg) setStatusMessage(msg);
        if (next === 'dropped' || next === 'disconnected') {
          if (next === 'disconnected') {
            engine.stop();
            setIsStreaming(false);
          }
        }
      },
      onDevicesUpdated: (list) => {
        setDevices(list);
      },
      onLatencySample: (rtt) => {
        const oneWay = Math.max(2, Math.round(rtt / 2) + activeProfile.bufferFrames * 6);
        setMetrics((prev) => ({
          ...prev,
          estimatedLatencyMs:
            prev.estimatedLatencyMs === 0
              ? oneWay
              : Math.round(prev.estimatedLatencyMs * 0.65 + oneWay * 0.35),
        }));
      },
      onConnectAck: (rx) => {
        setActiveTarget(rx);
        setStatusMessage(`${rx.name} (${rx.ip}:${rx.port}) ile bağlantı kuruldu.`);
        startCapturePipeline();
      },
      onConnectError: (reason) => {
        setIsStreaming(false);
        engine.stop();
        setStatusMessage(reason);
      },
      onMirrorStopped: (reason) => {
        engine.stop();
        setIsStreaming(false);
        setStatusMessage(reason);
      },
    });

    transportRef.current = client;
    client.connectSocket();

    const fpsTimer = window.setInterval(() => {
      const count = frameCounterRef.current;
      frameCounterRef.current = 0;
      setMetrics((prev) => ({
        ...prev,
        fps: count,
        droppedFrames: droppedCounterRef.current,
        framesTransferred: totalSentRef.current,
      }));
    }, 1000);

    return () => {
      window.clearInterval(fpsTimer);
      engine.stop();
      client.disconnect();
      onConnectionStateChange('disconnected');
    };
  }, []);

  useEffect(() => {
    captureRef.current?.updateProfile(activeProfile);
    setMetrics((prev) => ({
      ...prev,
      resolutionLabel: `${activeProfile.width}×${activeProfile.height}`,
      bitrateKbps: activeProfile.bitrateKbps,
    }));
  }, [activeProfile]);

  const startCapturePipeline = async () => {
    const engine = captureRef.current;
    if (!engine) return;
    frameCounterRef.current = 0;
    droppedCounterRef.current = 0;
    totalSentRef.current = 0;
    const result = await engine.start(sourceType);
    setIsStreaming(true);
    if (result.warning) {
      setStatusMessage(result.warning);
    } else {
      setStatusMessage('Ekran akışı yerel ağ üzerinden aktarılıyor.');
    }
  };

  const handleScanDevices = async () => {
    setIsScanning(true);
    setStatusMessage('Yerel ağdaki alıcı cihazlar taranıyor...');
    try {
      const res = await fetch('/api/discovery/scan', { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data.devices)) {
          setDevices(data.devices);
          setStatusMessage(
            data.devices.length > 0
              ? `${data.devices.length} alıcı cihaz bulundu.`
              : 'Yerel ağda açık alıcı cihaz bulunamadı. Alıcı TV ekranını açın veya IP girin.',
          );
        }
      }
      transportRef.current?.requestDiscoveryList();
    } catch {
      setStatusMessage('Ağ taraması sırasında bir bağlantı hatası oluştu.');
    } finally {
      window.setTimeout(() => setIsScanning(false), 400);
    }
  };

  const handleSelectPreset = (nextPreset: QualityPresetId) => {
    setPresetId(nextPreset);
    saveQualityPreset(nextPreset);
  };

  const handleSelectDevice = (device: DiscoveredDevice) => {
    setSelectedDeviceId(device.id);
    setManualIp(device.ip);
    setManualPort(String(device.port));
    setEndpointError(null);
    saveManualEndpoint(device.ip, String(device.port));
    setActiveTarget({ name: device.name, ip: device.ip, port: device.port });
  };

  const handleManualConnectSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const validation = validateManualEndpoint(manualIp, manualPort);
    if (!validation.valid || !validation.ip || !validation.port) {
      setEndpointError(validation.error ?? 'Geçersiz IP veya port.');
      return;
    }

    setEndpointError(null);
    saveManualEndpoint(validation.ip, String(validation.port));
    setStatusMessage(`${validation.ip}:${validation.port} adresi doğrulanıyor...`);

    try {
      const probeRes = await fetch('/api/devices/probe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ip: validation.ip, port: validation.port }),
      });
      const probe = await probeRes.json();
      if (!probe.reachable) {
        setEndpointError(`${validation.ip}:${validation.port} adresine yerel ağda erişilemedi.`);
        return;
      }
      if (probe.receiver) {
        setSelectedDeviceId(probe.receiver.id);
        setActiveTarget(probe.receiver);
      } else {
        setSelectedDeviceId(null);
        setActiveTarget({
          name: `Manuel Hedef (${validation.ip})`,
          ip: validation.ip,
          port: validation.port,
        });
      }
      transportRef.current?.connectSocket(validation.ip, validation.port);
      transportRef.current?.pairWithReceiver({
        targetDeviceId: probe.receiver?.id,
        targetIp: validation.ip,
        targetPort: validation.port,
        presetId,
      });
    } catch {
      setEndpointError('Hedef adres yoklanırken ağ hatası oluştu.');
    }
  };

  const handleStartMirroring = async () => {
    setEndpointError(null);

    if (selectedDeviceId) {
      transportRef.current?.pairWithReceiver({
        targetDeviceId: selectedDeviceId,
        presetId,
      });
      return;
    }

    const validation = validateManualEndpoint(manualIp, manualPort);
    if (!validation.valid || !validation.ip || !validation.port) {
      setEndpointError(
        devices.length > 0
          ? 'Listeden bir alıcı cihaz seçin veya geçerli bir IP/Port girin.'
          : validation.error ?? 'Geçerli bir alıcı IP adresi ve port girin.',
      );
      return;
    }

    saveManualEndpoint(validation.ip, String(validation.port));
    transportRef.current?.pairWithReceiver({
      targetIp: validation.ip,
      targetPort: validation.port,
      presetId,
    });
  };

  const handleStopMirroring = () => {
    captureRef.current?.stop();
    setIsStreaming(false);
    transportRef.current?.stopMirroring();
    setStatusMessage('Yansıtma durduruldu.');
  };

  const handlePhotoSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const img = await mediaModuleRef.current.loadImageFile(file);
      captureRef.current?.setPhotoSource(img);
      setSourceType('media_photo');
      setMediaModuleInfo(mediaModuleRef.current.getInfo());
      setStatusMessage(`Fotoğraf paylaşıma hazır: ${file.name}`);
    } catch (err) {
      setStatusMessage(err instanceof Error ? err.message : 'Görsel yüklenemedi.');
    }
  };

  const handleToggleAudioModule = () => {
    const next = !audioModuleRef.current.isEnabled();
    setAudioModuleInfo(audioModuleRef.current.setEnabled(next));
  };

  const statusMeta = CONNECTION_STATE_META[connectionState];

  return (
    <motion.main
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.24, ease: [0.16, 1, 0.3, 1] }}
      className="max-w-7xl mx-auto px-4 sm:px-6 py-6 sm:py-8"
    >
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Primary Workspace (8 cols) */}
        <div className="lg:col-span-8 space-y-6">
          {/* 1. Primary Stream Control & Live Preview */}
          <section className="rounded-2xl bg-[#11161F] border border-slate-800/90 p-5 sm:p-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800/80">
              <div>
                <h1 className="font-display text-xl sm:text-2xl font-bold text-white">
                  Verici Kontrol Paneli
                </h1>
                <p className="mt-1 text-sm text-slate-300" data-testid="transmitter-status-msg">
                  {statusMessage}
                </p>
              </div>

              <div className="flex items-center gap-3 shrink-0">
                {connectionState === 'dropped' && (
                  <button
                    type="button"
                    data-dpad="true"
                    onClick={handleStartMirroring}
                    className="dpad-focusable flex items-center gap-2 px-4 py-3 rounded-xl bg-amber-500/15 border border-amber-500/40 text-amber-200 text-sm font-semibold hover:bg-amber-500/25 whitespace-nowrap"
                  >
                    <RefreshCw className="h-4 w-4" />
                    <span>Yeniden Bağlan</span>
                  </button>
                )}

                {!isStreaming ? (
                  <button
                    type="button"
                    data-dpad="true"
                    data-testid="start-mirroring-btn"
                    onClick={handleStartMirroring}
                    className="dpad-focusable flex items-center gap-2.5 px-6 py-3 rounded-xl bg-[#00D4FF] text-[#051018] text-sm font-bold hover:bg-[#33DFFF] shadow-[0_0_24px_rgba(0,212,255,0.22)] whitespace-nowrap"
                  >
                    <Play className="h-4 w-4 fill-current" />
                    <span>Yansıtmayı Başlat</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    data-dpad="true"
                    data-testid="stop-mirroring-btn"
                    onClick={handleStopMirroring}
                    className="dpad-focusable flex items-center gap-2.5 px-6 py-3 rounded-xl bg-rose-500 text-white text-sm font-bold hover:bg-rose-400 shadow-[0_0_20px_rgba(244,63,94,0.28)] whitespace-nowrap"
                  >
                    <Square className="h-4 w-4 fill-current" />
                    <span>Durdur</span>
                  </button>
                )}
              </div>
            </div>

            {/* Live Preview + 4-Column Telemetry Strip */}
            <div className="mt-5 grid grid-cols-1 md:grid-cols-12 gap-5 items-center">
              <div className="md:col-span-5 w-full min-w-0 rounded-xl bg-[#090C11] border border-slate-800 overflow-hidden aspect-video flex items-center justify-center relative">
                {previewUrl ? (
                  <img
                    src={previewUrl}
                    alt="Canlı yansıtma önizlemesi"
                    className="w-full h-full object-contain block"
                  />
                ) : (
                  <div className="flex flex-col items-center text-center px-4">
                    <Monitor className="h-8 w-8 text-slate-600 mb-2" />
                    <span className="text-xs text-slate-400">
                      Yayın başladığında giden kare önizlemesi burada görünür
                    </span>
                  </div>
                )}
              </div>

              <div className="md:col-span-7 grid grid-cols-2 gap-3">
                <div className="rounded-xl bg-[#151C28] border border-slate-800/80 p-3.5 flex flex-col justify-between">
                  <span className="text-xs text-slate-400">Tahmini Gecikme</span>
                  <span className="font-mono-tabular text-2xl font-bold text-white mt-1" data-testid="metric-latency">
                    {isStreaming || connectionState === 'connected' ? `${metrics.estimatedLatencyMs} ms` : '—'}
                  </span>
                </div>

                <div className="rounded-xl bg-[#151C28] border border-slate-800/80 p-3.5 flex flex-col justify-between">
                  <span className="text-xs text-slate-400">Anlık Kare Hızı</span>
                  <span className="font-mono-tabular text-2xl font-bold text-[#00D4FF] mt-1" data-testid="metric-fps">
                    {isStreaming ? `${metrics.fps} FPS` : `0 / ${activeProfile.fps} FPS`}
                  </span>
                </div>

                <div className="rounded-xl bg-[#151C28] border border-slate-800/80 p-3.5 flex flex-col justify-between">
                  <span className="text-xs text-slate-400">Çözünürlük ve Bit Hızı</span>
                  <span className="font-mono-tabular text-sm sm:text-base font-semibold text-slate-100 mt-1">
                    {activeProfile.width}×{activeProfile.height} · {(activeProfile.bitrateKbps / 1000).toFixed(1)} Mbps
                  </span>
                </div>

                <div className="rounded-xl bg-[#151C28] border border-slate-800/80 p-3.5 flex flex-col justify-between min-w-0">
                  <span className="text-xs text-slate-400">Aktif Hedef Cihaz</span>
                  {activeTarget ? (
                    <div className="mt-1 min-w-0" data-testid="metric-target">
                      <div className="text-sm font-semibold text-white truncate">{activeTarget.name}</div>
                      <div className="font-mono-tabular text-xs text-[#00D4FF] truncate">
                        {activeTarget.ip}:{activeTarget.port}
                      </div>
                    </div>
                  ) : (
                    <span className="text-sm font-semibold text-slate-400 mt-1" data-testid="metric-target">
                      Hedef Seçilmedi
                    </span>
                  )}
                </div>
              </div>
            </div>
          </section>

          {/* 2. LAN Auto Discovery & Manual IP/Port Connection */}
          <section className="rounded-2xl bg-[#11161F] border border-slate-800/90 p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
              <div>
                <h2 className="font-display text-lg font-bold text-white">
                  Yerel Ağ Cihaz Keşfi (LAN)
                </h2>
                <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
                  UDP yayın keşfi (Port {networkInfo?.udpDiscoveryPort ?? 41234}) ve yerel ağ soketi ile alıcı TV&apos;leri bulun.
                </p>
              </div>
              <button
                type="button"
                data-dpad="true"
                data-testid="scan-lan-btn"
                onClick={handleScanDevices}
                className="dpad-focusable flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#182130] border border-slate-700/80 text-sm font-semibold text-slate-100 hover:border-[#00D4FF]/60 whitespace-nowrap"
              >
                <Search className={`h-4 w-4 text-[#00D4FF] ${isScanning ? 'animate-spin' : ''}`} />
                <span>{isScanning ? 'Taranıyor...' : 'Ağı Tara'}</span>
              </button>
            </div>

            {/* Discovered Device List */}
            {devices.length > 0 ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6" data-testid="discovered-devices-list">
                {devices.map((dev) => {
                  const isSelected = selectedDeviceId === dev.id;
                  return (
                    <button
                      key={dev.id}
                      type="button"
                      data-dpad="true"
                      data-testid={`device-item-${dev.id}`}
                      onClick={() => handleSelectDevice(dev)}
                      className={`dpad-focusable text-left p-4 rounded-xl border transition-colors flex items-center justify-between gap-3 ${
                        isSelected
                          ? 'bg-[#00D4FF]/10 border-[#00D4FF] text-white'
                          : 'bg-[#161D2A] border-slate-800 text-slate-200 hover:border-slate-600'
                      }`}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div className="h-10 w-10 rounded-lg bg-[#0E131C] border border-slate-700/70 flex items-center justify-center shrink-0">
                          <Tv className={`h-5 w-5 ${isSelected ? 'text-[#00D4FF]' : 'text-slate-300'}`} />
                        </div>
                        <div className="min-w-0">
                          <div className="font-semibold text-sm truncate">{dev.name}</div>
                          <div className="font-mono-tabular text-xs text-slate-400 mt-0.5">
                            {dev.ip}:{dev.port} · {dev.source === 'udp' ? 'UDP Keşif' : 'Yerel Ağ'}
                          </div>
                        </div>
                      </div>
                      <span
                        className={`text-xs font-medium shrink-0 ${
                          dev.status === 'ready' ? 'text-emerald-400' : 'text-amber-400'
                        }`}
                      >
                        {dev.status === 'ready' ? 'Hazır' : 'Yayında'}
                      </span>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div
                data-testid="empty-devices-notice"
                className="mb-6 rounded-xl bg-[#0D121A] border border-dashed border-slate-800 p-5 text-center"
              >
                <p className="text-sm text-slate-300">
                  Henüz otomatik keşfedilen alıcı cihaz yok.
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  TV cihazınızda veya ikinci bir sekmede &quot;Alıcı Modu (TV Ekranı)&quot;nu açın, ardından &quot;Ağı Tara&quot;ya basın ya da aşağıdan manuel IP girin.
                </p>
              </div>
            )}

            {/* Manual IP & Port Form */}
            <form onSubmit={handleManualConnectSubmit} className="pt-4 border-t border-slate-800/80">
              <div className="text-sm font-semibold text-slate-200 mb-3">
                Manuel IP Adresi ve Port ile Bağlan
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-12 gap-3 items-end">
                <div className="sm:col-span-6">
                  <label htmlFor="manual-ip-input" className="block text-xs text-slate-400 mb-1.5">
                    Hedef IP Adresi
                  </label>
                  <input
                    id="manual-ip-input"
                    data-dpad="true"
                    type="text"
                    value={manualIp}
                    onChange={(e) => {
                      setManualIp(e.target.value);
                      setSelectedDeviceId(null);
                      if (endpointError) setEndpointError(null);
                    }}
                    placeholder="Örn: 192.168.1.42"
                    className="dpad-focusable w-full h-11 px-3.5 rounded-xl bg-[#0B0F16] border border-slate-700/80 text-sm font-mono-tabular text-white placeholder:text-slate-500"
                  />
                </div>
                <div className="sm:col-span-3">
                  <label htmlFor="manual-port-input" className="block text-xs text-slate-400 mb-1.5">
                    Port
                  </label>
                  <input
                    id="manual-port-input"
                    data-dpad="true"
                    type="text"
                    value={manualPort}
                    onChange={(e) => {
                      setManualPort(e.target.value);
                      if (endpointError) setEndpointError(null);
                    }}
                    placeholder="3000"
                    className="dpad-focusable w-full h-11 px-3.5 rounded-xl bg-[#0B0F16] border border-slate-700/80 text-sm font-mono-tabular text-white placeholder:text-slate-500"
                  />
                </div>
                <div className="sm:col-span-3">
                  <button
                    type="submit"
                    data-dpad="true"
                    data-testid="manual-connect-btn"
                    className="dpad-focusable w-full h-11 px-4 rounded-xl bg-[#182232] border border-[#00D4FF]/40 text-sm font-semibold text-[#00D4FF] hover:bg-[#00D4FF]/15 whitespace-nowrap"
                  >
                    IP ile Bağlan
                  </button>
                </div>
              </div>
              {endpointError && (
                <div
                  role="alert"
                  data-testid="manual-endpoint-error"
                  className="mt-3 flex items-center gap-2 text-xs sm:text-sm text-rose-400"
                >
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{endpointError}</span>
                </div>
              )}
            </form>
          </section>

          {/* 3. Capture Source & Extensible Phase-2 Modules */}
          <section className="rounded-2xl bg-[#11161F] border border-slate-800/90 p-5 sm:p-6">
            <h2 className="font-display text-lg font-bold text-white mb-1">
              Yayın Kaynağı ve Genişletilebilir Modüller
            </h2>
            <p className="text-xs sm:text-sm text-slate-400 mb-4">
              MVP odak noktası canlı ekran yakalamadır; fotoğraf paylaşımı ve ses aktarımı modüler yapıda hazırdır.
            </p>

            <div className="flex flex-wrap gap-2 mb-5">
              <button
                type="button"
                data-dpad="true"
                onClick={() => setSourceType('display')}
                className={`dpad-focusable px-4 py-2.5 rounded-xl text-sm font-medium border whitespace-nowrap ${
                  sourceType === 'display'
                    ? 'bg-[#00D4FF]/15 border-[#00D4FF] text-white'
                    : 'bg-[#161E2B] border-slate-800 text-slate-300 hover:border-slate-600'
                }`}
              >
                Ekran Yakalama
              </button>
              <button
                type="button"
                data-dpad="true"
                onClick={() => setSourceType('test_canvas')}
                className={`dpad-focusable px-4 py-2.5 rounded-xl text-sm font-medium border whitespace-nowrap ${
                  sourceType === 'test_canvas'
                    ? 'bg-[#00D4FF]/15 border-[#00D4FF] text-white'
                    : 'bg-[#161E2B] border-slate-800 text-slate-300 hover:border-slate-600'
                }`}
              >
                Canlı Test Akışı
              </button>
              <button
                type="button"
                data-dpad="true"
                onClick={() => fileInputRef.current?.click()}
                className={`dpad-focusable flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium border whitespace-nowrap ${
                  sourceType === 'media_photo'
                    ? 'bg-[#00D4FF]/15 border-[#00D4FF] text-white'
                    : 'bg-[#161E2B] border-slate-800 text-slate-300 hover:border-slate-600'
                }`}
              >
                <ImageIcon className="h-4 w-4 text-[#00D4FF]" />
                <span>Fotoğraf Seç ve Yansıt</span>
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handlePhotoSelected}
                className="sr-only"
                aria-label="Paylaşılacak fotoğraf seçin"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-4 border-t border-slate-800/80">
              <div className="rounded-xl bg-[#151C27] border border-slate-800/80 p-4 flex items-start justify-between gap-3">
                <div className="flex items-start gap-3">
                  <Volume2 className="h-5 w-5 text-[#00D4FF] mt-0.5 shrink-0" />
                  <div>
                    <div className="text-sm font-semibold text-white">
                      {audioModuleInfo.name} · <span className="text-xs text-slate-400 font-normal">{audioModuleInfo.phase}</span>
                    </div>
                    <p className="text-xs text-slate-400 mt-1">{audioModuleInfo.summary}</p>
                  </div>
                </div>
                <button
                  type="button"
                  data-dpad="true"
                  onClick={handleToggleAudioModule}
                  className={`dpad-focusable px-3 py-1.5 rounded-lg text-xs font-semibold border shrink-0 whitespace-nowrap ${
                    audioModuleInfo.status === 'active'
                      ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300'
                      : 'bg-[#1B2433] border-slate-700 text-slate-300'
                  }`}
                >
                  {audioModuleInfo.status === 'active' ? 'Hazır (Açık)' : 'Hazır'}
                </button>
              </div>

              <div className="rounded-xl bg-[#151C27] border border-slate-800/80 p-4 flex items-start gap-3">
                <ImageIcon className="h-5 w-5 text-[#00D4FF] mt-0.5 shrink-0" />
                <div>
                  <div className="text-sm font-semibold text-white">
                    {mediaModuleInfo.name} · <span className="text-xs text-slate-400 font-normal">{mediaModuleInfo.phase}</span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1">{mediaModuleInfo.summary}</p>
                </div>
              </div>
            </div>
          </section>
        </div>

        {/* Right Column: Quality Presets & Connection Lifecycle (4 cols) */}
        <aside className="lg:col-span-4 space-y-6">
          <section className="rounded-2xl bg-[#11161F] border border-slate-800/90 p-5 sm:p-6">
            <div className="flex items-center gap-2.5 mb-4">
              <Sliders className="h-5 w-5 text-[#00D4FF]" />
              <h2 className="font-display text-lg font-bold text-white">
                Kalite ve Performans Ön Ayarları
              </h2>
            </div>

            <div className="space-y-3" data-testid="quality-presets-list">
              {QUALITY_PRESET_LIST.map((preset) => {
                const active = preset.id === presetId;
                return (
                  <button
                    key={preset.id}
                    type="button"
                    data-dpad="true"
                    data-testid={`preset-${preset.id}`}
                    onClick={() => handleSelectPreset(preset.id)}
                    className={`dpad-focusable w-full text-left p-4 rounded-xl border transition-colors ${
                      active
                        ? 'bg-[#00D4FF]/10 border-[#00D4FF] text-white'
                        : 'bg-[#151C28] border-slate-800 text-slate-200 hover:border-slate-600'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-sm sm:text-base">{preset.label}</span>
                      <span className="font-mono-tabular text-xs font-semibold text-[#00D4FF]">
                        {preset.fps} FPS
                      </span>
                    </div>
                    <p className="text-xs text-slate-400 mt-1 leading-relaxed">{preset.description}</p>
                    <div className="mt-2.5 flex items-center gap-2 font-mono-tabular text-[11px] text-slate-300">
                      <span>{preset.width}×{preset.height}</span>
                      <span aria-hidden="true">·</span>
                      <span>{(preset.bitrateKbps / 1000).toFixed(1)} Mbps</span>
                      <span aria-hidden="true">·</span>
                      <span>Tampon: {preset.bufferFrames} kare</span>
                    </div>
                  </button>
                );
              })}
            </div>
          </section>

          <section className="rounded-2xl bg-[#11161F] border border-slate-800/90 p-5 sm:p-6">
            <div className="flex items-center gap-2.5 mb-3">
              <Activity className="h-5 w-5 text-[#00D4FF]" />
              <h2 className="font-display text-lg font-bold text-white">
                Bağlantı Durumu
              </h2>
            </div>
            <p className="text-sm text-slate-200 font-medium">{statusMeta.label}</p>
            <p className="text-xs text-slate-400 mt-1">{statusMeta.description}</p>

            <div className="mt-4 pt-4 border-t border-slate-800/80 space-y-2 text-xs">
              <div className="flex items-center justify-between text-slate-300">
                <span>Aktarılan Kare</span>
                <span className="font-mono-tabular">{metrics.framesTransferred}</span>
              </div>
              <div className="flex items-center justify-between text-slate-300">
                <span>Atlanan Kare (Tampon)</span>
                <span className="font-mono-tabular">{metrics.droppedFrames}</span>
              </div>
              <div className="flex items-center justify-between text-slate-300">
                <span>Yerel Ağ Adresi</span>
                <span className="font-mono-tabular">{networkInfo?.lanIp ?? '—'}</span>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </motion.main>
  );
}
