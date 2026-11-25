// Read-only statistics over existing卦例 records. Civil dates are stored in
// local time (RecordsStore.stamp), so ISO/UTC conversion would shift cutoffs.
function civilKey(date) {
  return date.getFullYear() * 10000 + (date.getMonth() + 1) * 100 + date.getDate();
}

// Convert a validated civil date to a calendar ordinal, not a duration in
// local time: a DST change must not shift a record's column on the graphic.
function civilOrdinal(key) {
  const t = new Date(0);
  t.setUTCFullYear(Math.floor(key / 10000), Math.floor(key / 100) % 100 - 1, key % 100);
  return t.getTime() / 86400000;
}

function readCivilKey(value) {
  if (typeof value !== 'string') return null;
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:$|[ T])/.exec(value.trim());
  if (!match) return null;
  const [, y, m, d] = match.map(Number);
  const check = new Date(y, m - 1, d, 12);
  const key = y * 10000 + m * 100 + d;
  return civilKey(check) === key ? key : null;
}

function localDate(year, month, day) {
  return new Date(year, month, day, 12);
}

function period(now, month) {
  const y = now.getFullYear(), m = now.getMonth(), d = now.getDate();
  const monday = d - (now.getDay() + 6) % 7;
  const start = localDate(y, m, month ? 1 : monday);
  const end = month ? localDate(y, m + 1, 1) : localDate(y, m, monday + 7);
  return { start, end, last: localDate(end.getFullYear(), end.getMonth(), end.getDate() - 1) };
}

export function priorHuiguanPeriod(now = new Date(), range = 'week') {
  const monthly = range === 'month';
  const { start: boundary } = period(now, monthly);
  const anchor = localDate(boundary.getFullYear(), boundary.getMonth(), boundary.getDate() - 1);
  const { start } = period(anchor, monthly);
  return { range, anchor, key: String(civilKey(start)) };
}

const clean = (value) => typeof value === 'string' ? value.trim() : '';

export function summarizeHuiguan(records, now = new Date(), range = 'week') {
  const { start, end, last } = period(now, range === 'month');
  const startKey = civilKey(start), endKey = civilKey(end);
  const firstOrdinal = civilOrdinal(startKey);
  const dayCount = civilOrdinal(endKey) - firstOrdinal;
  const ids = new Set();
  const valid = [];
  for (const record of records) {
    if (!record || record.deleted) continue;
    const date = readCivilKey(record.t);
    if (date === null || date < startKey || date >= endKey) continue;
    const id = clean(record.id);
    if (id && ids.has(id)) continue;
    if (id) ids.add(id);
    valid.push({ record, dayIndex: civilOrdinal(date) - firstOrdinal });
  }

  const counts = new Map();
  let noted = 0;
  for (const { record } of valid) {
    const label = clean(record.cat) || '未分类';
    counts.set(label, (counts.get(label) || 0) + 1);
    if (clean(record.note)) noted += 1;
  }
  const categories = [...counts]
    .map(([label, count]) => ({ id: label, label, count }))
    .sort((a, b) => b.count - a.count || (a.label > b.label ? 1 : a.label < b.label ? -1 : 0));
  return { start, last, dayCount, total: valid.length, categories, noted, marks: valid.map(({ record, dayIndex }, index) => ({
    index, dayIndex,
    label: clean(record.cat) || '未分类', noted: Boolean(clean(record.note)),
  })) };
}
