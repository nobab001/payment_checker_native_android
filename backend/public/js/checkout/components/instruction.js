import { esc } from '../utils.js';

/** Thin instruction under provider name — no background chip. */
export function renderInstruction(instruction) {
  const text = (instruction || '').trim();
  if (!text) return '';
  return `<div class="instruction-area" role="note">${esc(text)}</div>`;
}
