// This module's vocabulary for screening configuration, mapped onto the design system's five
// tones — same reasoning as status.js: the design system knows no business words.
import { TONES, toneMapper } from './design-system';

export const riskTone = toneMapper({
  LOW: TONES.POSITIVE,
  MEDIUM: TONES.WARNING,
  HIGH: TONES.NEGATIVE,
});

export const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH'];

export function dateTime(iso) {
  return iso ? new Date(iso).toLocaleString() : '—';
}
