/** Shared native plugin controls; preserve the host font and color scheme. */
export function NativeControlStyles() {
  return <style>{`
    .skillhub-controls :is(button, input, select) {
      font: inherit; color: inherit; background: transparent; border: 1px solid color-mix(in srgb, currentColor 28%, transparent);
      border-radius: 8px; padding: 8px 12px; box-sizing: border-box; max-width: 100%;
    }
    .skillhub-controls button { cursor: pointer; min-height: 40px; }
    .skillhub-controls button:not(:disabled):hover { background: color-mix(in srgb, currentColor 7%, transparent); }
    .skillhub-controls button:disabled { opacity: .5; cursor: not-allowed; }
    .skillhub-controls .skillhub-danger { color: light-dark(#b42318, #ffb4ab); }
    .skillhub-controls dialog { border: 1px solid color-mix(in srgb, currentColor 28%, transparent); }
    .skillhub-controls :is(button, input, select):focus-visible { outline: 2px solid currentColor; outline-offset: 3px; }
    .skillhub-controls button + button { margin-inline-start: 8px; }
  `}</style>;
}
