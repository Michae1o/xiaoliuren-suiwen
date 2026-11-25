// Decorative effects only. The authoritative term, stats and date range live elsewhere.
const effects = new Map([
  ['立春','spring'], ['雨水','rain'], ['惊蛰','thunder'], ['春分','swallows'],
  ['清明','blossoms'], ['谷雨','ripples'],
  ['立夏','canopy'], ['小满','grainfill'], ['芒种','grain'], ['夏至','solstice-day'],
  ['小暑','lotus-cool'], ['大暑','heat-haze'], ['立秋','turning-leaf'], ['处暑','cloudbreak'],
  ['白露','dew'], ['秋分','equinox-dusk'], ['寒露','grass-dew'],
  ['霜降','frost'], ['立冬','first-ice'], ['小雪','first-snow'], ['大雪','snow'],
  ['冬至','returning-dawn'], ['小寒','icicles'], ['大寒','deep-ice'],
]);
export function getHuiguanMaterial(season) {
  return effects.get(season?.term) ?? null;
}

const renderers = {
  spring: new Set(['swallows','blossoms','cloudbreak']),
  autumn: new Set(['turning-leaf','grass-dew']),
  sun: new Set(['equinox-dusk','solstice-day','lotus-cool','heat-haze','first-ice']),
  winter: new Set(['first-snow','returning-dawn','icicles','deep-ice']),
};
export function getHuiguanMaterialRenderer(material) {
  return Object.keys(renderers).find(group => renderers[group].has(material)) ?? null;
}
