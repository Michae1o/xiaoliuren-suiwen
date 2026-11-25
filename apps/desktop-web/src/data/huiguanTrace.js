// Data encoding for the decorative SVG layer. Do not treat its bounded sample
// as statistics. The report always renders the full underlying counts.
export const TRACE_LIMIT = 72;
const MAX_CLUSTERS = 9;

export function buildHuiguanTrace(marks, dayCount, categories) {
  const days = Math.max(1, Math.trunc(dayCount) || 1);
  const perDay = new Map();
  const perCategory = new Map();
  const perStatus = [0, 0];
  const labels = categories.slice(0, categories.length > MAX_CLUSTERS ? MAX_CLUSTERS-1 : MAX_CLUSTERS).map(({label}) => label);
  const all = marks.map((mark,index) => ({...mark,index})).sort((a,b) => a.dayIndex-b.dayIndex || a.index-b.index);
  const sampled = all.length > TRACE_LIMIT;
  const drawn = sampled ? Array.from({length:TRACE_LIMIT},(_,i) => all[Math.floor((i+.5)*all.length/TRACE_LIMIT)]) : all;
  const overflow = categories.length > MAX_CLUSTERS;
  const clusterCount = labels.length + Number(overflow);
  const points = drawn.map(mark => {
    const day = Math.min(days-1,Math.max(0,Math.trunc(mark.dayIndex)||0));
    const found = labels.indexOf(mark.label);
    const category = found >= 0 ? found : Math.max(0,clusterCount-1);
    const status = mark.noted ? 0 : 1;
    const dayRank = perDay.get(day) || 0, categoryRank = perCategory.get(category) || 0, statusRank = perStatus[status]++;
    perDay.set(day,dayRank+1);
    perCategory.set(category,categoryRank+1);
    return {index:mark.index,day,dayRank,category,categoryRank,status,statusRank};
  });
  return {
    days, sampled, overflow,
    points: points.map(point => ({...point,
      dayCount: perDay.get(point.day),
      categoryCount: perCategory.get(point.category),
      statusCount: perStatus[point.status],
    })),
    labels: overflow ? [...labels,'其余事类'] : labels,
    perCategory: Array.from({length:clusterCount},(_,i) => perCategory.get(i)||0),
  };
}
