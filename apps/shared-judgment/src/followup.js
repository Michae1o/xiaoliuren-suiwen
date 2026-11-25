/* followup.js —— 「接着问」（追问）提示词的**唯一一份**。
 *
 * 为什么单列一份：桌面 / 安卓 / iOS 的界面各写各的，但**内容必须一模一样**。
 * 此前点明：「各端无法统一判据模块吗？只是格式不一样，内容一样没问题。」
 * ⇒ 规矩文本与标签只准在这里；端上要么 import（JS 两端），要么用生成常量（Kotlin 两端）。
 * ⛔ 不许在任何端上再抄一遍这些句子；改口径只改这里。
 *
 * 口径依据：`09_逐份读记.md` 补正＋W41（接着问＝在原卦上转太极点）。
 */

/** 开场白：一句把「同一卦、不另起」说死。 */
export const FOLLOW_HEAD = '请接着这一卦继续答（接前卦：同一卦内追问，不另起卦）。';

/** 分段标签：端上只管排版，字一个都不许换。 */
export const FOLLOW_LABEL = {
  judge: '【这一卦的判据 · 与解卦时同一份】',
  duanci: '【当时的断辞】',
  asked: '【这一卦已经答过的】',
  first: '（这是第一次追问）',
  ask: '【这次要问的】',
  rules: '【续问规矩】',
  qPre: '问',
  aPre: '答',
  colon: '：',
};

/** 续问规矩（②′ 是「转太极点」那一条，别删；2026-11-25 撤掉「括注依据」那条，出处不再进断辞）。 */
export const FOLLOW_RULES = [
  '① 只用这一卦：不另起、不重起，答案要从这一卦里得出；',
  '② 落宫为主：先看这一问在这卦里对应到哪座宫、什么关系，再答（问什么，是什么）；',
  '②′ 这一问就是在同一卦里**转太极点**（换主体，不换卦）：先定这一问的太极点落在哪座宫、转的是关系与状态；要换的是根本（问的是另一件事）就归③，直接说「这不在这一卦里」，不要硬转。',
  '③ 这一问若不在这一卦里（问的是旁人别的事、或无端之问），直接说「这不在这一卦里」并讲清原因，不要硬答；',
  '④ 不要主动再问、不要邀着继续问、不展开没问到的东西；',
  '⑤ 格式同主断辞（【结论】【断辞】…），宁短勿长、够用即止。',
];

/**
 * 拼出「接着问」的整段 user 消息。
 * @param {{judgeText?:string, duanci?:string, thread?:Array<{q:string,a:string}>, q:string}} o
 *   judgeText 判据整段（与解卦时同一份，缺了它模型就在体系外跑）
 *   duanci    当时的断辞（不给，它会绕过自己上次说的话重新猜）
 *   thread    已经答过的问答
 *   q         这次要问的
 */
export function followupUser(o) {
  const L = [FOLLOW_HEAD, ''];
  if (o && o.judgeText) { L.push(FOLLOW_LABEL.judge, String(o.judgeText), ''); }
  if (o && o.duanci) { L.push(FOLLOW_LABEL.duanci, String(o.duanci).trim(), ''); }
  L.push(FOLLOW_LABEL.asked);
  const th = (o && o.thread) || [];
  if (!th.length) L.push(FOLLOW_LABEL.first);
  th.forEach(function (it, i) {
    L.push(FOLLOW_LABEL.qPre + (i + 1) + FOLLOW_LABEL.colon + it.q);
    L.push(FOLLOW_LABEL.aPre + (i + 1) + FOLLOW_LABEL.colon + (it.a || ''));
  });
  L.push('', FOLLOW_LABEL.ask, String((o && o.q) || ''), '', FOLLOW_LABEL.rules);
  FOLLOW_RULES.forEach(function (r) { L.push(r); });
  return L.join('\n');
}
