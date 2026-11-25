/* 视觉用节气，不参与起卦、判据或回观统计。时刻按 UTC 绝对瞬间比较。
 * 2024–2028 使用香港天文台《二十四节气日期及时间》表（公布精度：分钟）：
 * https://www.hko.gov.hk/en/gts/astronomy/Solar_Term.htm
 * 表外以简化太阳视黄经分段估算，只用于主题，交节附近可能有分钟级误差。
 */
const TERMS = [
  ['春分','昼夜平分','ink','#132b26','#bce6ac','#69a78b','#366753'],
  ['清明','万物清明','dew','#122925','#bed7bd','#64a499','#396960'],
  ['谷雨','雨生百谷','rain','#142824','#cbd59d','#60aba6','#42684b'],
  ['立夏','万物并秀','ink','#14291e','#d2e7a7','#84ad63','#335d40'],
  ['小满','青穗初盈','dew','#222c18','#ece497','#9db064','#576d36'],
  ['芒种','风吹麦浪','grain','#302a17','#f0cd83','#c5a45f','#76653a'],
  ['夏至','昼长至极','sun','#302019','#ffe08b','#e2995d','#8d5034'],
  ['小暑','温风初至','sun','#30201c','#ffd294','#df8a67','#8b453a'],
  ['大暑','流火盛时','sun','#311d1e','#ffc47f','#e77760','#923b36'],
  ['立秋','一叶知秋','grain','#2d271b','#efc68a','#9aa371','#655c3b'],
  ['处暑','暑气渐收','dew','#242b26','#ded0a4','#8fafa3','#4d6451'],
  ['白露','露凝为白','dew','#1c2b2c','#d4e3d8','#91b6b0','#435e5b'],
  ['秋分','秋色平分','grain','#2b2622','#dfc996','#b3886a','#705642'],
  ['寒露','清露微寒','dew','#202731','#dce2d4','#94a8bc','#414f70'],
  ['霜降','霜色入林','frost','#28252f','#e9deda','#a8a9cc','#615368'],
  ['立冬','万物渐藏','ink','#172431','#c8ddd9','#819ead','#38536a'],
  ['小雪','雪意初临','frost','#141d30','#dce5fb','#93b6e3','#47628f'],
  ['大雪','雪满长空','frost','#1b2231','#edf0ff','#a4b4d2','#576782'],
  ['冬至','长夜向明','sun','#262432','#f6dfab','#928bad','#6a5278'],
  ['小寒','霜月初深','frost','#1a2536','#e0e7f2','#97b7c5','#425a7c'],
  ['大寒','岁寒将尽','frost','#20283a','#ecedf4','#a5a3c4','#52597b'],
  ['立春','草木知春','ink','#1e3027','#c8dba5','#82b596','#486d55'],
  ['雨水','春雨润物','rain','#192b2e','#c7e0cc','#74acb2','#43706d'],
  ['惊蛰','春雷始鸣','rain','#212839','#d8d8b7','#a39dc4','#545e7e'],
];
const HKO = {
  2024: '01060449 01202207 02041627 02191213 03051023 03201106 04041502 04192200 05050810 05202100 06051210 06210451 07062220 07221544 08070809 08222255 09071111 09222044 10080300 10230615 11070620 11220356 12062317 12211721',
  2025: '01051033 01200400 02032210 02181807 03051607 03201701 04042049 04200356 05051357 05210255 06051757 06211042 07070405 07222129 08071352 08230434 09071652 09230219 10080841 10231151 11071204 11220936 12070505 12212303',
  2026: '01051623 01200945 02040402 02182352 03052159 03202246 04050240 04200939 05051949 05210837 06052348 06211625 07070957 07230313 08071943 08231019 09072241 09230805 10081429 10231738 11071752 11221523 12071053 12220450',
  2027: '01052210 01201530 02040946 02190533 03060340 03210425 04050817 04201518 05060125 05211418 06060526 06212211 07071537 07230905 08080127 08231614 09080428 09231402 10082017 10232333 11072339 11222116 12071638 12221042',
  2028: '01060355 01202122 02041531 02191126 03050925 03201017 04041403 04192109 05050712 05202010 06051116 06210402 07062130 07221454 08070721 08222201 09071022 09221945 10080209 10230513 11070527 11220254 12062225 12211620',
};
const mod = (x, n) => ((x % n) + n) % n;
const sin = (degrees) => Math.sin(degrees * Math.PI / 180);
const INSTANTS = Object.entries(HKO).flatMap(([year, row]) => row.split(' ').map((stamp, i) => ({
  index: (i + 19) % 24,
  time: Date.UTC(Number(year), Number(stamp.slice(0, 2)) - 1, Number(stamp.slice(2, 4)),
    Number(stamp.slice(4, 6)) - 8, Number(stamp.slice(6))),
})));
const COVERAGE_START = Date.UTC(Number(Object.keys(HKO)[0]), 0, 1) - 8 * 3600000;
const COVERAGE_END = Date.UTC(Number(Object.keys(HKO).at(-1)) + 1, 0, 1) - 8 * 3600000;
function solarLongitude(ms) {
  const t = (ms / 86400000 + 2440587.5 - 2451545) / 36525;
  const meanLongitude = 280.46646 + t * (36000.76983 + 0.0003032 * t);
  const meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t);
  const center = (1.914602 - t * (0.004817 + 0.000014 * t)) * sin(meanAnomaly)
    + (0.019993 - 0.000101 * t) * sin(2 * meanAnomaly)
    + 0.000289 * sin(3 * meanAnomaly);
  return mod(meanLongitude + center - 0.00569 - 0.00478 * sin(125.04 - 1934.136 * t), 360);
}
export function getHuiguanSeason(date = new Date()) {
  const time = Number(date);
  if (!Number.isFinite(time)) return getHuiguanSeason();
  const angle = solarLongitude(time);
  let index = Math.floor(angle / 15), phase = angle / 15 - index;
  if (time >= COVERAGE_START && time < COVERAGE_END) {
    const next = INSTANTS.findIndex((x) => x.time > time);
    if (next === 0) index = 18; // early January before 小寒 remains 冬至
    else if (next > 0) {
      const last = INSTANTS[next - 1];
      index = last.index;
      phase = (time - last.time) / (INSTANTS[next].time - last.time);
    } else index = INSTANTS.at(-1).index;
  }
  const [term, tagline, mode, ink, accent, secondary, glow] = TERMS[index];
  return { term, nextTerm: TERMS[(index + 1) % 24][0], phase, tagline,
    colors: { ink, accent, secondary, glow }, mode, angle, index };
}
