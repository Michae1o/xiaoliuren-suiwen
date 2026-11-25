// Device-local calendar; one independently tracked reminder per week and month.
import { priorHuiguanPeriod, summarizeHuiguan } from './huiguanStats.js';

export const HUIGUAN_REMINDER_KEY = 'xlr-huiguan-reminded-v1';

export const HUIGUAN_CARD_MS = 8000;
export const HUIGUAN_GUIDE_MS = 5200;

// Take the date of the last day itself and form [last day 21:00, day 2 00:00).
// In the first three hours the current period, not the previous one, is due.
export function huiguanReminderWindow(now, range) {
  if (!Number.isFinite(now?.getTime())) return null;
  const y = now.getFullYear(), m = now.getMonth(), d = now.getDate();
  const month = range === 'month';
  const endOfPeriod = month
    ? new Date(y, m + 1, 0, 21)
    : new Date(y, m, d + (7 - now.getDay()) % 7, 21);
  const firstDay = month ? d === 1 : now.getDay() === 1;
  const opens = now >= endOfPeriod ? endOfPeriod : firstDay ? new Date(y, m, d - 1, 21) : null;
  if (!opens) return null;
  const boundary = new Date(opens.getFullYear(), opens.getMonth(), opens.getDate() + 1, 12);
  const expires = new Date(opens.getFullYear(), opens.getMonth(), opens.getDate() + 2);
  if (now >= expires) return null;
  return { ...priorHuiguanPeriod(boundary, range), opens, expires };
}

export function pickHuiguanReminder(records, now, seen = {}) {
  for (const range of ['month', 'week']) {
    const candidate = huiguanReminderWindow(now, range);
    if (!candidate) continue;
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
