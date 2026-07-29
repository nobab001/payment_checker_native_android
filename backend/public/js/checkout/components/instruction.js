import { esc } from '../utils.js';

/** Provider-level instruction / message block (Sleek 1-line thin note). */
export function renderInstruction(instruction) {
  const text = (instruction || '').trim();
  if (!text) return '';
  return `<div class="instruction-area" role="note">ℹ ${esc(text)}</div>`;
}
