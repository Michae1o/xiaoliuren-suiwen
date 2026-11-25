/* quickReadCore.js —— 「本机速断」的正文生成（进「叁 断卦」步自动出那段）。
 *
 * ★ 口径：去 AI 味、说人话。判据逻辑一个字没动，
 *    只把**措辞**改成当面说话的口气——先给答案，再讲两句人话；
 *    去掉破折号、「」套宫名、排比和客服腔。改前改后同一批问题出的结论必须一一对应。
 *
 * 与前几版的关系（逻辑部分照旧）：
 *   1. 吉凶三态：①问吉凶→吉凶当答案；②不问吉凶→答案在落宫特性；③问了但吉凶不足以回答→以特性为主。
 *   2. 前两宫只解释"为什么走到这一步"，不当独立吉凶。
 *   3. 生克是关系：生＝化、往外递；克＝想去拿、占主动。⛔ 不用"添力/拿住/卡住"这类自造口语。
 *   4. 应期两种问法：问"什么时候"→定点（季节＋年份，要属相才能钉死）；问"还有多久"→拿本宫三数当时长。
 *   5. 学员口径不进依据。
 *
 * 长度纪律：结论一句话 + 理由最多一句，不铺判据、不列类象清单。
 *
 * ★ 体用不进速断：不分析体用，结论从简。
 *   ① 体用整段摘掉 —— 不再算 bodyUse，也不再出现「用生体 / 体克用」这类话；
 *   ② 结论更短 —— 理由只留一句，三宫走向压成一行。
 *
 * ★ 同源改造：
 *   ① 入口函数补参数通道：优先取 c.cat，未传则以 autoCat(q) 自动补全 CATS[catKey]；
 *   ② 结论句优先取 c.cat.luo[c.g3] 压成一句白话（≤20字），拿不到再退回现有分支；
 *   ③ 理由句优先从 c.cat.extra(c) 取一条最相关的白话理由（≤60字）；
 *   ④ 2026-11-24：ASKS 三表整体废弃，天气/失物/适不适合改从 CATS 的 luo 与 rules 取；
 *   ⑤ 留连落宫保留「还没定，眼下还拖着」；否定问句翻转；不碰体用。
 */
import { G, SHENG, KE, CATS, ZWX, autoCat } from '@xlr/shared-judgment';

/* ---------- 关系：原文义，不带吉凶、不自造词 ---------- */

/* ---------- 落宫性质白话（从判据里切前两段） ---------- */
function tag(g) {
  const x = String((G[g] && G[g].xing) || '').replace(/——.*$/, '');
  const ps = x.split(/[、，]/).filter(s => s && String(s).trim());
  return ps.slice(0, 2).join('、') || x;
}

/* 说话口气：破折号改逗号，去引号，别让句子读起来像稿子 */
function speak(s) {
  return String(s == null ? '' : s)
    .replace(/——/g, '，')
    .replace(/，，/g, '，')
    .replace(/[「」]/g, '');
}

/* ---------- 三宫走向：前因 → 经过 → 结果（材料里的「相承关系」） ----------
   依据（原话）：
   · 「天、地、人，三宫是指前因、经过、结果，这就是一个事情的相承关系」｜028 L421｜A
   · 「前两宫只看事情的可能性」「发展的状况」｜028 L455 / L463｜A
   · 「天时小吉…小吉生人宫大安木，为得到财」｜011 L665｜A（前宫生人宫＝得／成）
   · 「天时又克地宫，说明财动，财空」；「天时克地宫原本为得，可却所得为空」｜011 L737 / L758｜A（克＝去取得，但可能落空／有阻碍）
   · 「天克地，不代表地对人没作用，无非有阻碍」｜031 L278｜A
   · 落宫凶照样断凶，前两宫生只作"怎么解决时可利用"｜029 L457｜A
   ⛔ 不把前两宫写成一个"会虚掉"的独立主体（材料无此说法）。 */
/* ★ 删除：原先这里有 pairRel + T2D + D2R 三件（「这里别住了」「中间压着起头」
   「结果压住中间，前面铺的路到后面用不上」…）。那些是**自造话术**，把天宫地宫当成了有吉凶
   的独立主体，与判据第一原则第二条「天地宫没有吉凶」直接冲突。老师讲的是：
   落宫定结果；天地只用来找「哪些不足、哪些太过导致了现在这个结果」（J-34-02／J-30-02），
   三宫本身就是「天＝起因、地＝过程、人＝结果」的相承关系（J-40-02）。 */

/* ---------- 应期：定点（什么时候）vs 时长（还有多久） ---------- */
const SEASON = [
  [/长夏/, [5, 7], '长夏'], [/冬春/, [2, 4], '冬春之交'], [/辰巳|四季土/, [4, 6], '春末夏初'],
  [/春/, [2, 4], '春天'], [/秋/, [8, 10], '秋天'], [/冬/, [11, 1], '冬天']
];
function seasonOf(GG) {
  const ji = String(GG.ji || '');
  for (const [re, span, name] of SEASON) if (re.test(ji)) return { span, name };
  return { span: [3, 5], name: '这一季' };
}
function whenPoint(GG, now) {
  const { span, name } = seasonOf(GG);
  const near = /吉$/.test(String(GG.jx));
  let off = near ? 0 : 1;
  const m = now.getMonth() + 1;
  if (span[0] <= span[1] ? m > span[1] : (m > span[1] && m < span[0])) off += 1;
  return (off === 0 ? '今年' : off === 1 ? '明年' : '后年') + name + '，' + span[0] + ' 到 ' + span[1] + ' 月';
}
function durationOf(GG) {
  const n = parseInt(String(GG.san || '').split(/[·\s]+/)[0], 10) || 3;
  const a = String(GG.act || '');
  const unit = a === '快' ? ['天', '周'] : (a === '动' ? ['周', '个月'] : ['个月', '季']);
  return { n, unit, txt: n + ' ' + unit[0] + '到 ' + n + ' ' + unit[1] };
}

/* ---------- 是非问句判定词（跟问法走） ---------- */
const VD = [['会不会', '会'], ['能不能', '能'], ['可不可以', '可以'], ['可否', '可以'], ['有没有', '有'],
  ['行不行', '行'], ['该不该', '该'], ['是不是', '是'], ['是否', '是'],
  ['顺利', '顺'], ['成功', '成'], ['成不成', '成']];

/* ---------- 清洗与文本压缩辅助 ---------- */
function cleanExtraText(t) {
  if (!t) return '';
  return String(t)
    .replace(/<[^>]+>/g, '')
    .replace(/（\s*\d+[^）]*）/g, '')
    .replace(/\(\s*\d+[^)]*\)/g, '')
    .replace(/[「」『』]/g, '')
    .replace(/[—–——]+/g, '，')
    .replace(/体用决定要不要主动找[：:]?/g, '')
    .replace(/以天为体看天气、以人为体看吉凶[。，]?/g, '')
    .replace(/体用|用生体|体生用|用克体|体克用|比助|比劫|比和/g, '')
    .replace(/依据|判据|卦例|原文说/g, '')
    .replace(/[0-9]{3}\s*(行[0-9]+)?/g, '')
    .replace(/天克人／人克天/g, '生克')
    .replace(/\s+/g, '')
    /* 删词后可能只剩开头标点（如「用生体——…」删掉「用生体」余「，…」），去掉才成句 */
    .replace(/^[，,、；;：:]+/, '');
}

function compressReason(raw) {
  const s = cleanExtraText(raw);
  if (!s) return '';
  const sentences = s.split(/[；。]/).filter(Boolean);
  let res = '';
  for (const sent of sentences) {
    if (!sent) continue;
    const candidate = res ? (res + '；' + sent) : sent;
    if ((candidate + '。').length <= 60) {
      res = candidate;
    } else {
      break;
    }
  }
  if (!res && sentences[0]) {
    res = sentences[0].slice(0, 58);
  }
  if (!res) return '';
  /* 末尾已有标点（？ ！ 等）就不再补句号，避免「干嘛呢？。」这种连标点 */
  return /[。？！?!…]$/.test(res) ? res : (res + '。');
}

function compressLuoVerdict(raw) {
  let stripped = String(raw || '')
    .replace(/<[^>]+>/g, '')
    .replace(/（[^）]*）/g, '')
    .replace(/\([^)]*\)/g, '')
    .replace(/[「」『』]/g, '')
    .replace(/——/g, '，')
    .replace(/\s+/g, '');

  const firstSentence = (stripped.split(/[；。]/)[0] || '').trim();
  if (!firstSentence) return '';
  if (firstSentence.length <= 20) return firstSentence;
  const parts = firstSentence.split(/[，、]/);
  let acc = '';
  for (const p of parts) {
    if (!p) continue;
    const candidate = acc ? (acc + '，' + p) : p;
    if (candidate.length <= 20) {
      acc = candidate;
    } else {
      break;
    }
  }
  return (acc && acc.length >= 4) ? acc : firstSentence.slice(0, 20);
}

/* ---------- 现有硬编码表（兜底用，不许删） ---------- */
/* ---------- 事类落宫断法（数据驱动 · 2026-11-24 全量重做） ----------
   判据真源：CATS[catKey].luo（52 条落宫断辞，来自 108 份材料）。
   早期版本疾病类 38 行硬编码 + fallbackLuoVerdict 84 行旧断辞表 —— 整段删除。 */
function luoVerdict(c, ans, negQ, good) {
  const cat = CATS[c.catKey];
  const g3 = c.g3;

  /* 疾病：判据明文「病源看首宫（天宫），不看落宫」 */
  if (c.catKey === 'ill') {
    const raw = (cat && cat.luo && (cat.luo[c.g1] || cat.luo[g3])) || '';
    const t = compressLuoVerdict(raw);
    if (t) return '病源看天宫：' + t;
  }

  /* 本类的落宫断辞 */
  let t = compressLuoVerdict((cat && cat.luo && cat.luo[g3]) || '');
  if (t) return t;

  /* 本类没有（如射覆 0 条）→ 退回通用宫性；材料未载的类要说在前面 */
  t = compressLuoVerdict((CATS.general && CATS.general.luo && CATS.general.luo[g3]) || '');
  if (t) return (cat && cat.coverage === 'none') ? '本类材料未载，只按宫性：' + t : t;

  return null;
}

/* ---------- D 组取文（重做） ----------
   早期版本按旧 label（「关键」「方位」「格局」「变数」…）在 extra 里找，而新 extra 的条目名
   是**规则名**，旧 label 一个都对不上 ⇒ 永远走兜底。改为按关键词匹配规则名。 */

/* 天气 / 失物 / 适不适合：原来引的是 ASKS 三张手写表，改为从 CATS 取（与安卓 AskData.kt 同口径） */
function weatherOf(g) { return (CATS.weather && CATS.weather.luo && CATS.weather.luo[g]) || ''; }
function lostOf(g) { return (CATS.lost && CATS.lost.luo && CATS.lost.luo[g]) || ''; }
function marriageSuit(ok, g3) {
  return ok
    ? ('既然落宫' + g3 + '，那就是适合；结不结婚另说。')
    : '眼下还不适合，落宫这一宫不是能定下来的状态。';
}

const EXTRA_KW = {
  ill: ['病源', '空亡的判别', '三分', '小吉不等于'],
  lost: ['三宫分工', '问卦时', '体生用', '地生人', '三宫合看'],
  weather: ['为体', '五行公式', '问法'],
  law: ['牢狱', '官非'],
  marriage: ['心动即卦', '生你', '多开', '两地'],
  work: ['多选一', '卦意', '合伙', '克不动'],
  money: ['钱去了哪儿', '钱数', '取数', '三宫合看'],
  exam: ['完全结果', '取分数', '名次'],
  preg: ['落宫定性', '生男生女', '天数', '体用克'],
  fengshui: ['坐向', '三步', '天时宫为向'],
  intent: ['生克方向', '疾厄宫', '事业宫', '田宅宫'],
  travel: ['车祸', '未出行'],
  gamble: ['不建议', '长期算', '无应卦'],
};

function pickExtraReason(c) {
  const cat = CATS[c.catKey] || CATS.general;
  if (!cat || !cat.rules) return '';
  const kws = EXTRA_KW[c.catKey] || [];
  const hit = cat.rules.find(function (r) {
    return kws.some(function (k) { return k && r.name && r.name.indexOf(k) > -1; });
  }) || cat.rules.find(function (r) { return r.quote; });
  if (!hit) return '';
  /* ★ 理由句优先用 note（整理者的概括）；quote 是老师原话，常带「既然落宫大安」这类举例，
     直接给用户会被误读成本卦的宫 —— 所以 quote 里出现具体宫名且没有 note 时，宁可不给理由。 */
  const raw = hit.note || hit.quote || '';
  if (!hit.note && /大安|留连|速喜|赤口|小吉|空亡/.test(raw)) return '';
  return compressReason(raw);
}

export function quickRead(c, nowArg) {
  if (!c || !c.g3 || !G[c.g3]) return '';
  const g1 = c.g1, g2 = c.g2, g3 = c.g3, GG = G[g3], q = String(c.q || '');
  const now = (nowArg instanceof Date) ? nowArg : new Date();

  /* 补齐事类通道 */
  if (!c.catKey) c.catKey = autoCat(q);
  if (!c.cat || typeof c.cat.extra !== 'function') {
    c.cat = CATS[c.catKey] || CATS.general;
  }

  /* ---- 问法 ---- */
  const isWhere = /哪|何处|什么地方|何方|位置|方位/.test(q);
  const isPoint = /什么时候|何时|几时|哪天|几月|哪年/.test(q);
  const isDur = /还有多久|多久能|多长时间|几天|几个月|多久/.test(q);
  const isSuit = /适合|该不该|要不要嫁|要不要娶|值得/.test(q);
  const isYn = /吗|嘛|是否|能否|会不会|能不能|可不可以|有没有|行不行|该不该/.test(q) || /(.)不\1/.test(q);

  let head = '', why = [];

  /* ---- ① 结论：按问法给（吉凶三态） ---- */
  if (isWhere) {
    const keWx = KE[GG.wx];
    const keGong = ['大安', '留连', '速喜', '赤口', '小吉', '空亡'].find(function (x) { return G[x] && G[x].wx === keWx; });
    head = '**往' + ((keGong && G[keGong].fw) || GG.fw) + '那边找**';
    const r = pickExtraReason(c);
    if (r) why.push(r);
    else why.push('方向看的是落宫克的那一宫：它在哪边，就往哪边找，以你现在住的地方为中心。');
  } else if (isPoint) {
    head = '**' + whenPoint(GG, now) + '**';
    why.push('时间按这一宫管的季节推，吉的近一点，不吉的远一点。想钉到哪一年，得先知道你的属相。');
  } else if (isDur) {
    const d = durationOf(GG);
    head = '**大概 ' + d.txt + '**';
    why.push('这一宫自带三个数 ' + String(GG.san).replace(/\s/g, ' ') + '，问"还有多久"就拿它当个量。数只是参考，不是日期。');
  } else if (isSuit && c.catKey === 'marriage') {
    const ok = /吉$/.test(String(GG.jx));
    head = '**' + (ok ? '适合' : '眼下还不适合') + '**';
    why.push(marriageSuit(ok, g3));
  } else if (c.catKey === 'weather') {
    const d1 = compressLuoVerdict(weatherOf(g1)), d2 = compressLuoVerdict(weatherOf(g2)), d3 = compressLuoVerdict(weatherOf(g3));
    const askRain = /会下雨|会下雪|下不下|下雨吗|下雪吗|会不会下|要下吗|下不下雨/.test(q);
    const snow = /雪/.test(q);
    if (askRain) {
      head = '**' + (/雨|雪/.test(d3) ? '会' : '不会') + '**';
      const r = pickExtraReason(c);
      if (r) why.push(r);
      else why.push('先看落宫：落到' + g3 + '，天象是' + d3 + '，这就是结果；天宫' + g1 + '是' + d1 + '（气）、地宫' + g2 + '是' + d2 + '（地理气温），这两宫是形成它的条件。');
    } else {
      head = '**' + d3 + '**';
      why.push('先定落宫：落到' + g3 + '，天象是' + d3 + '，这就是天气本身；天宫' + g1 + '与地宫' + g2 + '是形成它的条件。');
    }
  } else if (isYn) {
    if (!head) {
      const lost = c.catKey === 'lost' ? compressLuoVerdict(lostOf(g3)) : null;
      if (lost) {
        head = '**' + lost.replace(/。$/, '') + '**';
        const r = pickExtraReason(c);
        if (r) why.push(r);
      } else {
        let verb = '';
        for (const [k, v] of VD) if (q.indexOf(k) > -1) { verb = v; break; }
        if (!verb) verb = q.indexOf('会') > -1 ? '会' : q.indexOf('能') > -1 ? '能' : '是';
        const yes = /吉$/.test(String(GG.jx));
        const negQ = /严重|要紧|危险|厉害|更糟|恶化|有没有问题|有没有事|难吗/.test(q);
        const good = negQ ? !yes : yes;
        const bian = /变数/.test(String(GG.xing || ''));

        if (c.catKey === 'ill') {
          /* 疾病类：由三宫格局统一判定，不凭落宫单论 */
          const ans = good ? verb : (verb === '有' ? '没有' : '不' + verb);
          const lv = luoVerdict(c, ans, negQ, good);
          head = '**' + (lv || ans) + '**';
          const r = pickExtraReason(c);
          if (r) why.push(r);
        } else if (bian) {
          head = '**还没定，眼下还拖着**';
          const r = pickExtraReason(c);
          if (r) why.push(r);
          else why.push('落宫是留连：六宫里只有它自带变数，不是成了，也不是没成，是还缠着。');
        } else {
          const ans = good ? verb : (verb === '有' ? '没有' : '不' + verb);
          const lv = luoVerdict(c, ans, negQ, good);
          if (lv) {
            head = '**' + lv + '**';
          } else {
            head = '**' + ans + '**';
          }
          const r = pickExtraReason(c);
          if (r) why.push(r);
        }
      }
    }
  } else {
    /* 不问吉凶的泛问：答案在落宫特性里，吉凶只作括注 */
    head = '**' + tag(g3) + '**';
    const r = pickExtraReason(c);
    if (r) {
      why.push(r);
    } else {
      why.push('你没问吉凶，那先看这一宫是什么脾气（'
        + (String(GG.jx) === String(g3) ? g3 : g3 + '，' + GG.jx) + '）。');
    }
  }

  /* ---- ② 理由：只留**一句** ---- */
  const isWeather = c.catKey === 'weather' && !!(CATS.weather && CATS.weather.luo);
  if (!why.length && !isWeather) {
    /* ★ 不再用「前头克住后头／结果压住中间」那套自造话术。
       判据第一原则第二条（J-34-02／J-30-02）写明「天地宫没有吉凶，只有客观地去分析不足，
       或者说太过而导致了最后的结果」；三宫本身的定性是「天＝起因、地＝过程、人＝结果」
       的相承关系（J-40-02）。下面是这两条的原话压缩，不是新的推断。 */
    const isBad = /凶$/.test(String(GG.jx));
    why.push(isBad
      ? '天宫、地宫不看吉凶，只看哪些不足、哪些太过导致了这个结果。'
      : '天是起因、地是过程、人是结果，三宫相承。');
  }
  if (!why.length) why.push('最后落在' + g3 + '，' + String(GG.xing || '').replace(/[。；\s]+$/, '') + '。');

  return speak(head) + '\n\n' + why.slice(0, 1).map(speak).join('\n');
}

export const QuickReadCore = { quickRead };
