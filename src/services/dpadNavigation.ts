/**
 * Spatial D-Pad / TV Remote Keyboard Navigation Handler
 * Supports ArrowUp, ArrowDown, ArrowLeft, ArrowRight, Enter, and Escape/Backspace.
 */
export function attachDpadNavigation(onBack?: () => void): () => void {
  const handleKeyDown = (e: KeyboardEvent) => {
    const activeEl = document.activeElement as HTMLElement | null;
    const isInputFocused =
      activeEl &&
      (activeEl.tagName === 'INPUT' ||
        activeEl.tagName === 'TEXTAREA' ||
        activeEl.isContentEditable);

    if (e.key === 'Escape' || (e.key === 'Backspace' && !isInputFocused)) {
      if (onBack) {
        e.preventDefault();
        onBack();
      }
      return;
    }

    if (isInputFocused && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
      return;
    }

    const directionMap: Record<string, 'up' | 'down' | 'left' | 'right'> = {
      ArrowUp: 'up',
      ArrowDown: 'down',
      ArrowLeft: 'left',
      ArrowRight: 'right',
    };

    const dir = directionMap[e.key];
    if (!dir) return;

    const candidates = Array.from(
      document.querySelectorAll<HTMLElement>('[data-dpad="true"]:not([disabled])'),
    ).filter((el) => {
      const rect = el.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0;
    });

    if (candidates.length === 0) return;

    e.preventDefault();

    if (!activeEl || !candidates.includes(activeEl)) {
      candidates[0].focus();
      return;
    }

    const currentRect = activeEl.getBoundingClientRect();
    const cx = currentRect.left + currentRect.width / 2;
    const cy = currentRect.top + currentRect.height / 2;

    let bestEl: HTMLElement | null = null;
    let bestScore = Infinity;

    for (const candidate of candidates) {
      if (candidate === activeEl) continue;
      const rect = candidate.getBoundingClientRect();
      const tx = rect.left + rect.width / 2;
      const ty = rect.top + rect.height / 2;
      const dx = tx - cx;
      const dy = ty - cy;

      const isMatch =
        (dir === 'right' && dx > 12) ||
        (dir === 'left' && dx < -12) ||
        (dir === 'down' && dy > 12) ||
        (dir === 'up' && dy < -12);

      if (!isMatch) continue;

      const primaryDist = dir === 'left' || dir === 'right' ? Math.abs(dx) : Math.abs(dy);
      const orthogonalDist = dir === 'left' || dir === 'right' ? Math.abs(dy) : Math.abs(dx);
      const score = primaryDist + orthogonalDist * 2.2;

      if (score < bestScore) {
        bestScore = score;
        bestEl = candidate;
      }
    }

    if (bestEl) {
      bestEl.focus();
      bestEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
    }
  };

  window.addEventListener('keydown', handleKeyDown);
  return () => window.removeEventListener('keydown', handleKeyDown);
}
