// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "VERIFIED", "CLEAR" and "SIGNED" too. This module's own vocabulary is "screening":
// CLEAR · REVIEW · HIT (tones.js's own comment names it) — not the orchestrator's ACCEPTED /
// REJECTED / REFERRED, which this module never stores or displays.
import { TONES, toneMapper } from './design-system';

export const statusTone = toneMapper({
  // finalOutcome / machineOutcome (ScreeningOutcome) — the screening verdict itself.
  CLEAR: TONES.POSITIVE,
  REVIEW: TONES.WARNING,
  HIT: TONES.NEGATIVE,
  // The row is opened before it is decided, so a fresh row briefly shows this — see decide().
  PENDING: TONES.INFO,
  // processingStatus (ProcessingStatus) — has the async decide() step finished yet.
  IN_PROGRESS: TONES.INFO,
  COMPLETE: TONES.NEUTRAL,
  // callbackStatus (CallbackStatus) — has the orchestrator been told.
  SENT: TONES.POSITIVE,
  FAILED: TONES.NEGATIVE,
});

/**
 * The outcomes the board filters on. `PENDING` is included because a row genuinely sits there
 * for the brief window between the `202` and the off-thread decide() completing — this module's
 * decisions are not synchronous, unlike the placeholder it replaced.
 */
export const STATUSES = ['CLEAR', 'REVIEW', 'HIT', 'PENDING'];


export function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString() : '—';
}
