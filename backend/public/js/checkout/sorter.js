/** Client-side provider / number ordering (uses API position fields only). */

const MAX = Number.MAX_SAFE_INTEGER;

export function sortNumbers(numbers) {
  return [...numbers].sort((a, b) => {
    const pa = a.sortOrder ?? MAX;
    const pb = b.sortOrder ?? MAX;
    if (pa !== pb) return pa - pb;
    return String(a.number).localeCompare(String(b.number));
  });
}

export function sortProviders(providers) {
  return [...providers].sort((a, b) => {
    const pa = a.sortOrder ?? MAX;
    const pb = b.sortOrder ?? MAX;
    if (pa !== pb) return pa - pb;
    return String(a.displayName).localeCompare(String(b.displayName));
  });
}

export function sortGateways(gateways) {
  return [...gateways].sort((a, b) => {
    const pa = Number(a.position);
    const pb = Number(b.position);
    const na = Number.isFinite(pa) ? pa : MAX;
    const nb = Number.isFinite(pb) ? pb : MAX;
    if (na !== nb) return na - nb;
    return (a.simSlot || 0) - (b.simSlot || 0);
  });
}
