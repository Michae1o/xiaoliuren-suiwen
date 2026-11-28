/* cast.js —— 起卦（三路分支）纯函数化
   逐字来自 blk4.js:49-61（clockNums / numVals）与 blk4.js:234-284（#btnGo handler 的三路分支）。
   **唯一改动：把 DOM 取值提成参数、把 new Date() 提成参数 d** —— 算式一字未改。 */
import { advance, lunarOf, zhi, GONG } from './judgment.js';

/* 识别或校验时钟制式：'h24' (1..24, 午夜=24) / 'h23' (0..23, 午夜=0) / 'h12' (1..12, 午夜=12) / 'h11' (0..11, 午夜=0) */
function normHourCycle(hc) {
  return hc === 'h24' || hc === 'h23' || hc === 'h12' || hc === 'h11' ? hc : '';
}

export function hourCycleFromPattern(pat) {
  var s = String(pat || '').replace(/'[^']*'/g, '');
  if (s.indexOf('k') >= 0) return 'h24';
  if (s.indexOf('H') >= 0) return 'h23';
  if (s.indexOf('h') >= 0) return 'h12';
  if (s.indexOf('K') >= 0) return 'h11';
  return '';
}

export function detectHourCycle(overrideHc) {
  var ov = normHourCycle(overrideHc);
  if (ov) return ov;
  try {
    if (typeof window !== 'undefined' && window.__XLR_HOUR_CYCLE__) {
      var w = normHourCycle(window.__XLR_HOUR_CYCLE__);
      if (w) return w;
    }
  } catch (eW) { }
  try {
    if (typeof Intl !== 'undefined' && typeof Intl.DateTimeFormat === 'function') {
      var d0 = new Date(2026, 0, 1, 0, 5, 0);
      var f = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });
      var opts = f.resolvedOptions ? f.resolvedOptions() : null;
      var hc = normHourCycle(opts && opts.hourCycle);
      if (!hc && typeof f.formatToParts === 'function') {
        var parts = f.formatToParts(d0);
        for (var i = 0; i < parts.length; i++) {
          if (parts[i].type === 'hour') {
            var v = parseInt(parts[i].value, 10);
            if (v === 24) hc = 'h24';
            else if (v === 12) hc = 'h12';
            else if (v === 0) hc = (opts && opts.hour12) ? 'h11' : 'h23';
            break;
          }
        }
      }
      if (!hc && opts && typeof opts.hour12 === 'boolean') hc = opts.hour12 ? 'h12' : 'h23';
      if (hc) return hc;
    }
  } catch (eI) { }
  return 'h23';
}

export function displayHour(hh24, hourCycle) {
  var hc = detectHourCycle(hourCycle);
  if (hc === 'h24') return hh24 === 0 ? 24 : hh24;
  if (hc === 'h12') return (hh24 % 12) === 0 ? 12 : (hh24 % 12);
  if (hc === 'h11') return hh24 % 12;
  return hh24;
}

/* 时间取数：按安装者机器时钟制式显示时数（24 / 0 / 12），raw 为显示数，nums 为 0→10 后的起卦步数；zi 始终按 24 小时真时刻算 */
export function clockNums(d, hourCycle) {
  var n = d || new Date();
  var hh24 = n.getHours(), mm = n.getMinutes();
  var hc = detectHourCycle(hourCycle);
  var hh = displayHour(hh24, hc);
  var raw = [hh, Math.floor(mm / 10), mm % 10];
  return {
    hh: hh,
    hh24: hh24,
    mm: mm,
    hourCycle: hc,
    raw: raw,
    nums: raw.map(function (v) { return v === 0 ? 10 : v; }),
    zi: ((hh24 + 1) >> 1) % 12 + 1
  };
}

/* blk4.js:56-61 numVals 逐字（去掉 document.getElementById 取值） */
export function normalizeNums(vals) {
  return [0, 1, 2].map(function (i) {
    var x = Math.abs(parseInt(vals[i], 10)) || 0;
    return x === 0 ? 10 : x;
  });
}

/* blk4.js:57-58 numsFilled 的纯版：三个数都非空 */
export function numsFilled(vals) {
  for (var i = 0; i < 3; i++) { if (vals[i] === '' || vals[i] === null || vals[i] === undefined) return false; }
  return true;
}

/* blk4.js:257 / 262 / 274 三处同一个出口算式 */
function finish(steps, zi) {
  var g1 = advance(0, steps[0]), g2 = advance(g1, steps[1]), g3 = advance(g2, steps[2]);
  return { steps: steps.slice(), zi: zi, gi: [g1, g2, g3], g: [GONG[g1], GONG[g2], GONG[g3]], ziName: zhi[zi - 1] };
}

/** 分支 A：时间取数。manualNums 传了且三个都填了 ⇒ 走「手改」那一路（blk4.js:248-255） */
export function castByClock(d, manualNums, hourCycle) {
  var t = clockNums(d, hourCycle);
  var manual = !!(manualNums && numsFilled(manualNums));
  var steps = manual ? normalizeNums(manualNums) : t.nums.slice();
  var r = finish(steps, t.zi);
  r.mode = manual ? 'clock-manual' : 'clock-auto';
  r.clock = t;
  var hasZero = t.raw.some(function (v) { return v === 0; });
  r.src = manual
    ? '时间取数·手改 ' + steps.join(' / ') + '，' + zhi[t.zi - 1] + '时'
    : '时间取数 ' + t.hh + ':' + (t.mm < 10 ? '0' : '') + t.mm + ' ⇒ ' + t.raw.join(' / ') +
      (hasZero ? '（0 按 10 取）' : '') + '，' + zhi[t.zi - 1] + '时';
  return r;
}

/** 分支 B：报数（blk4.js:258-265）。zi 不传 ⇒ 按当前钟点 */
export function castByNums(rawNums, zi, d) {
  var nn = normalizeNums(rawNums);
  var z = zi || ((((d || new Date()).getHours() + 1) >> 1) % 12 + 1);
  var r = finish(nn, z);
  r.mode = 'num';
  var rawJoined = [rawNums[0], rawNums[1], rawNums[2]].map(function (x) { return Math.abs(parseInt(x, 10)) || 0; }).join();
  r.src = '报数 ' + rawNums[0] + ' / ' + rawNums[1] + ' / ' + rawNums[2] +
    (nn.join() !== rawJoined ? '（0 按 10 取）' : '') + '，' + zhi[z - 1] + '时';
  return r;
}

/** 分支 C：公历日期 → 农历月日时（blk4.js:266-275）。越界返回 null（原样返回 null，不抛） */
export function castByDate(dateStr, zi, d) {
  var p = String(dateStr).split('-');
  var lu = lunarOf(+p[0], +p[1], +p[2]);
  if (!lu) return null;
  var z = zi || ((((d || new Date()).getHours() + 1) >> 1) % 12 + 1);
  var steps = [lu.m, lu.d, z];
  var r = finish(steps, z);
  r.mode = 'date';
  r.lunar = lu;
  r.src = '公历 ' + dateStr + ' ⇒ 农历 ' + lu.y + ' 年' + (lu.leap ? '闰' : '') + lu.m + ' 月' + lu.d + ' 日，' + zhi[z - 1] + '时';
  return r;
}
