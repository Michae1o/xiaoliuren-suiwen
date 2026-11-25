// Local calendar, no history queue: only each immediately prior period is due.
import { priorHuiguanPeriod, summarizeHuiguan } from './huiguanStats.js';

export const HUIGUAN_REMINDER_KEY = 'xlr-huiguan-reminded-v1';

export function pickHuiguanReminder(records, now, seen = {}) {
  for (const range of ['month', 'week']) {
    const candidate = priorHuiguanPeriod(now, range);
    if (String(seen?.[range] || '') >= candidate.key) continue;
    const { total } = summarizeHuiguan(records, candidate.anchor, range);
    if (total > 0) return { ...candidate, total };
  }
  return null;
}

let fallbackSeen = {};
export function getHuiguanReminded() {
  try { return { ...fallbackSeen, ...JSON.parse(localStorage.getItem(HUIGUAN_REMINDER_KEY) || '{}') }; }
  catch { return { ...fallbackSeen }; }
}

export function markHuiguanReminded({ range, key }) {
  const next = getHuiguanReminded();
  if (String(next[range] || '') >= key) return;
  next[range] = key;
  fallbackSeen = next;
  try { localStorage.setItem(HUIGUAN_REMINDER_KEY, JSON.stringify(next)); }
  catch { /* current session still deduplicates */ }
}
