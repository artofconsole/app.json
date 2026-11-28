import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import type { AppMode, ConnectionState } from './core/types';
import { loadLastMode, saveLastMode } from './core/persistence';
import { attachDpadNavigation } from './services/dpadNavigation';
import { SplashScreen } from './components/SplashScreen';
import { TopBar } from './components/TopBar';
import { ModeSelector } from './components/ModeSelector';
import { TransmitterView } from './components/TransmitterView';
import { ReceiverView } from './components/ReceiverView';

function parseUrlOverrides(): {
  autostart: boolean;
  forcedMode: AppMode | null;
} {
  try {
    const params = new URLSearchParams(window.location.search);
    const autostart = params.get('autostart') === '1';
    const modeParam = params.get('mode');
    const forcedMode: AppMode | null =
      modeParam === 'transmitter' || modeParam === 'receiver' ? modeParam : null;
    return { autostart, forcedMode };
  } catch {
    return { autostart: false, forcedMode: null };
  }
}

export default function App() {
  const overrides = parseUrlOverrides();
  const [showSplash, setShowSplash] = useState(!overrides.autostart && !overrides.forcedMode);
  const [savedMode, setSavedMode] = useState<AppMode | null>(() => loadLastMode());
  const [activeMode, setActiveMode] = useState<AppMode | null>(() => {
    if (overrides.forcedMode) return overrides.forcedMode;
    if (overrides.autostart) return loadLastMode();
    return null;
  });
  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');

  const handleSplashFinish = () => {
    setShowSplash(false);
    const persisted = loadLastMode();
    setSavedMode(persisted);
    if (persisted) {
      setActiveMode(persisted);
    }
  };

  const handleSelectMode = (mode: AppMode) => {
    saveLastMode(mode);
    setSavedMode(mode);
    setActiveMode(mode);
    setConnectionState('disconnected');
  };

  const handleChangeMode = () => {
    setActiveMode(null);
    setConnectionState('disconnected');
  };

  useEffect(() => {
    const detach = attachDpadNavigation(() => {
      if (showSplash) {
        handleSplashFinish();
      } else if (activeMode) {
        handleChangeMode();
      }
    });
    return detach;
  }, [showSplash, activeMode]);

  return (
    <div
      data-state={showSplash ? 'splash' : activeMode ?? 'mode_select'}
      className="min-h-screen bg-[#090C11] text-[#F1F5F9]"
    >
      <AnimatePresence>
        {showSplash && <SplashScreen onFinish={handleSplashFinish} />}
      </AnimatePresence>

      {!showSplash && (
        <>
          <TopBar
            mode={activeMode}
            connectionState={connectionState}
            onChangeMode={handleChangeMode}
          />

          <AnimatePresence mode="wait">
            {!activeMode && (
              <motion.div
                key="mode-selector"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.18 }}
              >
                <ModeSelector lastSavedMode={savedMode} onSelectMode={handleSelectMode} />
              </motion.div>
            )}

            {activeMode === 'transmitter' && (
              <motion.div
                key="transmitter-view"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.18 }}
              >
                <TransmitterView
                  connectionState={connectionState}
                  onConnectionStateChange={setConnectionState}
                />
              </motion.div>
            )}

            {activeMode === 'receiver' && (
              <motion.div
                key="receiver-view"
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.18 }}
              >
                <ReceiverView
                  connectionState={connectionState}
                  onConnectionStateChange={setConnectionState}
                  onChangeMode={handleChangeMode}
                />
              </motion.div>
            )}
          </AnimatePresence>
        </>
      )}
    </div>
  );
}
