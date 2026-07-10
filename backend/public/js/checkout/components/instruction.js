import { esc } from '../utils.js';

/** Provider-level instruction / message block. */
export function renderInstruction(instruction) {
  const text = (instruction || '').trim();
  if (!text) return '';
  return `<div class="instruction-area" role="note">${esc(text)}</div>`;
}
