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
  const stripped = String(raw || '')
    .replace(/<[^>]+>/g, '')
    .replace(/\*\*/g, '')
    .replace(/[「」『』]/g, '')
    .replace(/——/g, '，')
    .replace(/\s+/g, '')
    .trim();
  if (!stripped) return '';
  let segs = stripped.split(/[；。]/).map(function (s) {
    return s.replace(/^[，,]+|[，,]+$/g, '');
  }).filter(Boolean);
  /* 头一个分句若只是吉凶标签，就跳过它取后面的实义——否则整段只剩「中吉」两个字 */
  if (segs.length > 1 && /^(大吉|中吉|小吉|大凶|中凶|小凶|吉|凶)$/.test(segs[0])) segs = segs.slice(1);
  let acc = '';
  for (const seg of segs) {
    const cand = acc ? (acc + '；' + seg) : seg;
    if (acc && cand.length > 46) break;
    acc = cand;
    if (acc.length >= 40) break;
  }
  return acc.replace(/[；。]$/, '');
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

/* ---------- 速断正文（第三版）
   写法沿用上游那版：结论一行，下面一句话把「起于…途经…结果落在…」说完，再一句断辞。
   长度纪律：总共不超过三小段、一般 60–110 字；不铺类象清单、不列条目、不写依据。
   新判据里学到的（落宫定吉凶、本类落宫断辞、留连变数、空亡二象、天地不论吉凶）都压进那两句里，
   不单独成段；应期和取数只在问到了才各给一行。 */

const QS = {
  '大安': '安稳未动', '留连': '拖延纠缠', '速喜': '迅猛即至',
  '赤口': '口舌凶险', '小吉': '主动走动', '空亡': '落空不成',
};
const JX_ONLY = /^(大吉|中吉|小吉|大凶|中凶|小凶|吉|凶)$/;
const JX_TAG = /^(大吉|中吉|小吉|大凶|中凶|小凶|吉|凶)\s*[；;，,]?\s*/;

/* 出处号、内部标注、加粗，一律不上屏 */
function tidyRaw(s) {
  return String(s || '')
    .replace(/<[^>]+>/g, '')
    .replace(/⛔[^。；]*/g, '')
    .replace(/\s*J-\d{2}(-\d{2})?\s*：\s*/g, '：')
    .replace(/（?J-\d{2}(-\d{2})?）?/g, '')
    .replace(/（\d{3}） ?/g, '')
    .replace(/\*\*/g, '')
    .replace(/[ \t]+/g, ' ')
    .trim();
}

/* 取一句话：一两个分句，不超过 max 字；只剩吉凶标签就当没取到 */
function oneSentence(s, max) {
  const t = tidyRaw(s).replace(JX_TAG, '').replace(/^(但|而|故|所以)[，]?/, '');
  if (!t) return '';
  const parts = t.split(/[；。]/).map(function (x) { return x.trim(); }).filter(Boolean);
  let out = '';
  for (const p of parts) {
    if (!out) { out = p; continue; }
    if (out.length + 1 + p.length > max) break;
    out += '；' + p;
  }
  if (!out) return '';
  if (JX_ONLY.test(out)) return '';
  /* 第一句本身太长就截到 max 字（尽量切在顿号/逗号处） */
  if (out.length > max) {
    const head = out.slice(0, max);
    const cut = Math.max(head.lastIndexOf('、'), head.lastIndexOf('，'), head.lastIndexOf('；'));
    out = (cut > 8 ? head.slice(0, cut) : head);
  }
  return out.replace(/[；，、]$/, '') + '。';
}

/* 三宫那句里的一小截 */
function stub(s) {
  const t = tidyRaw(s);
  if (!t) return '';
  const first = t.split('。')[0];
  let out = first.length > 20 ? (first.split(/[；，]/)[0] || '') : first;
  out = out.replace(JX_TAG, '').replace(/[；，]$/, '').trim();
  if (!out || out.length < 3 || JX_ONLY.test(out)) return '';
  return out;
}

export function quickRead(c, nowArg) {
  if (!c || !c.g3 || !G[c.g3]) return '';
  const g3 = c.g3;
  const g1 = c.g1, g2 = c.g2;
  const GG = G[g3];
  const q = String(c.q || '');
  const now = (nowArg instanceof Date) ? nowArg : new Date();

  if (!c.catKey) c.catKey = autoCat(q);
  const cat = CATS[c.catKey] || CATS.general;
  const L = (cat && cat.luo) || null;

  const isPoint = /什么时候|何时|几时|哪天|几月|哪年/.test(q);
  const isDur = /还有多久|多久能|多长时间|几天|几个月|多久/.test(q);
  const isNum = /取数|数字|几个|多少|数几/.test(q);
  const yn = /吗|嘛|是否|能否|会不会|能不能|可不可以|有没有|行不行|该不该/.test(q) || /(.)不\1/.test(q);
  const yes = /吉$/.test(String(GG.jx));

  /* ---- 天气卦单开一支：天气不看吉凶，看落宫的天象（015/077：落宫为先，天地是条件） ---- */
  if (c.catKey === 'weather') {
    /* 天象只取一句话：有「——」就取破折号后面那句（原文把解释放在后面），太长按分句断，不许切在词中间 */
    const sky = (x) => {
      let t = tidyRaw(x);
      if (t.indexOf('——') > -1) t = t.split('——').pop();
      let out = oneSentence(t, 22).replace(/。$/, '');
      if (out.length > 24) {
        const head = out.slice(0, 24);
        const cut = Math.max(head.lastIndexOf('，'), head.lastIndexOf('、'), head.lastIndexOf('；'));
        out = cut > 6 ? head.slice(0, cut) : head;
      }
      return out;
    };
    const sky3 = sky(L && L[g3]);
    const sky1 = sky(L && L[g1]);
    const sky2 = sky(L && L[g2]);
    const askRain = /下雨|下雪|下不下|会不会下|要下吗|有雨|有雪/.test(q);
    const snow = /雪/.test(q);
    let wHead;
    let ok = true;
    if (askRain) {
      ok = (snow ? /雪|雨|雹|雷/ : /雨|雹|雷/).test(sky3);
      wHead = '**' + (ok ? '会' : '不会') + '（' + g3 + '：' + sky3 + '）**';
    } else {
      wHead = '**' + g3 + '：' + sky3 + '**';
    }
    /* 三宫里跟落宫不一样的才说出来，别三遍一样 */
    const bits = [];
    if (sky1 && sky1 !== sky3) bits.push('起于' + sky1);
    if (sky2 && sky2 !== sky3) bits.push('途经' + sky2);
    const wline = (bits.length ? (bits.join('，') + '，') : '') + '落宫' + g3 + '定天象：' + sky3 + '。';
    const wout = [wHead, wline];
    /* 095：问「会不会下（雨/雪）」不是纯天气问题，是掺了吉凶观念的问题——
       吉利于自己、凶不利于自己；两条路（天象 / 吉凶）要能对上。
       默认的倾向是「不希望下」（095「他不希望下雪啊」）；若问卦人是盼着的（比如南方人盼雪），以他自己说的为准。 */
    const good = /吉$/.test(String(GG.jx));
    if (askRain) {
      const favor = !ok;              /* 顺意＝不下 */
      if (favor === good) {
        wout.push('不管从天气看还是从吉凶看，都指向同一个方向：' + (ok ? '会下' : '不下')
          + '；' + GG.jx + (good ? '，也顺你的意' : '，不合你的意') + '。');
      } else {
        wout.push('从天气看是' + (ok ? '会下' : '不下') + '，从吉凶看是' + GG.jx
          + '（吉利于你、凶不利于你），两个方向不一致。盼着下就当会，不盼就按凶看。');
      }
    }
    if (isPoint && ok) {
      const se = seasonOf(GG);
      wout.push('时间看这一宫管的季节：' + se.name + '（' + se.span[0] + ' 到 ' + se.span[1] + ' 月）。');
    }
    return wout.join('\n\n');
  }

  /* ---- 一、结论：是非问按问句那个词答，其余给「吉凶 · 宫」 ---- */
  let head;
  if (yn) {
    const VERBS = [['会不会', '会'], ['能不能', '能'], ['能否', '能'], ['可不可以', '可以'], ['可否', '可以'],
      ['有没有', '有'], ['行不行', '行'], ['该不该', '该'], ['是不是', '是'], ['是否', '是'],
      ['顺不顺', '顺'], ['顺利吗', '顺'], ['适合', '适合']];
    let v = '';
    for (const [k, val] of VERBS) if (q.indexOf(k) > -1) { v = val; break; }
    if (!v) v = q.indexOf('能') > -1 ? '能' : (q.indexOf('会') > -1 ? '会' : (q.indexOf('有') > -1 ? '有' : '是'));
    const neg = (v === '有') ? '没有' : (v === '可以') ? '不可以' : (v === '顺') ? '不顺' : (v === '适合') ? '不适合' : '不' + v;
    const negQ = /有没有事|有没有问题|要紧|严重|危险|难吗|难过/.test(q);
    let ans = yes ? v : neg;
    if (negQ && v === '有') ans = yes ? '没有' : '有';
    head = '**' + ans + '（' + GG.jx + ' · ' + g3 + '）**';
  } else {
    head = '**' + ((String(GG.jx) === g3) ? g3 : (GG.jx + ' · ' + g3)) + '**';
  }

  /* ---- 二、一句断辞：本类的落宫断辞优先，没有就用这一宫的性子 ---- */
  const say = oneSentence(L && L[g3], 62) || oneSentence(GG.xing, 62);

  /* ---- 三、三宫一句（早期版本写法）：起于…，途经…，结果落在… ---- */
  /* 天/地两宫一律用六宫短标签：这样那句永远是一行短的，也不会把「病源看天宫」这类
     本类断法错塞到天宫头上（那是断辞该说的，不是起因）。 */
  const a1 = QS[c.g1] || '';
  const a2 = QS[c.g2] || '';
  const flow = (a1 && a2 && a1 === a2)
    ? ('起于、途经都是' + a1 + '，结果落在' + g3)
    : ('起于' + a1 + '，途经' + a2 + '，结果落在' + g3);
  const line = flow + '：' + say;

  const out = [head, line];

  /* ---- 四、应期 / 取数：问到了才给，各一行 ---- */
  if (isPoint) out.push('时间按这一宫管的季节推：' + whenPoint(GG, now) + '。');
  else if (isDur) out.push('这一宫自带 ' + String(GG.san).replace(/\s+/g, ' ') + '，当个量看：大概 ' + durationOf(GG).txt + '。');
  if (isNum) out.push('取数：这一宫带 ' + String(GG.san).replace(/\s+/g, '') + '，先看吉凶，再看数字。');

  return out.join('\n\n');
}

export const QuickReadCore = { quickRead };
