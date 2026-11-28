import { ArrowLeftRight, Wifi, WifiOff, RefreshCw, Zap } from 'lucide-react';
import type { AppMode, ConnectionState } from '../core/types';
import { CONNECTION_STATE_META } from '../core/connectionStateMachine';

interface TopBarProps {
  mode: AppMode | null;
  connectionState: ConnectionState;
  onChangeMode: () => void;
}

export function TopBar({ mode, connectionState, onChangeMode }: TopBarProps) {
  const statusMeta = CONNECTION_STATE_META[connectionState];

  const toneStyles: Record<typeof statusMeta.tone, string> = {
    neutral: 'text-slate-300 border-slate-700/70 bg-[#121721]',
    amber: 'text-amber-300 border-amber-500/40 bg-amber-500/10',
    green: 'text-emerald-300 border-emerald-500/40 bg-emerald-500/10',
    red: 'text-rose-300 border-rose-500/40 bg-rose-500/10',
  };

  const dotColors: Record<typeof statusMeta.tone, string> = {
    neutral: 'bg-slate-400',
    amber: 'bg-amber-400',
    green: 'bg-emerald-400',
    red: 'bg-rose-400',
  };

  return (
    <header className="w-full border-b border-slate-800/80 bg-[#0B0F16]/90 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[#131B28] border border-[#00D4FF]/35">
            <Zap className="h-5 w-5 text-[#00D4FF]" />
          </div>
          <div className="flex items-baseline gap-2.5 min-w-0">
            <span className="font-display text-xl font-bold tracking-tight text-white whitespace-nowrap shrink-0">
              Hız
            </span>
            {mode && (
              <span className="text-sm text-slate-400 truncate hidden sm:inline">
                {mode === 'transmitter' ? 'Verici Modu' : 'Alıcı Modu'}
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          {mode && (
            <div
              role="status"
              aria-live="polite"
              data-testid="connection-status-badge"
              className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs sm:text-sm font-medium whitespace-nowrap shrink-0 ${toneStyles[statusMeta.tone]}`}
            >
              {statusMeta.tone === 'red' ? (
                <WifiOff className="h-4 w-4 shrink-0" />
              ) : statusMeta.isAnimated && statusMeta.tone === 'amber' ? (
                <RefreshCw className="h-4 w-4 shrink-0 animate-spin" />
              ) : (
                <Wifi className="h-4 w-4 shrink-0" />
              )}
              <span
                aria-hidden="true"
                className={`h-2 w-2 rounded-full shrink-0 ${dotColors[statusMeta.tone]} ${
                  statusMeta.isAnimated ? 'animate-pulse' : ''
                }`}
              />
              <span>{statusMeta.label}</span>
            </div>
          )}

          {mode && (
            <button
              type="button"
              data-dpad="true"
              onClick={onChangeMode}
              className="dpad-focusable flex items-center gap-2 px-4 py-2 rounded-xl bg-[#161E2C] border border-slate-700/80 text-sm font-semibold text-slate-100 hover:border-[#00D4FF]/60 hover:text-white whitespace-nowrap shrink-0"
            >
              <ArrowLeftRight className="h-4 w-4 text-[#00D4FF]" />
              <span>Mod Değiştir</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
