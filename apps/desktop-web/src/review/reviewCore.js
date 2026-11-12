/* reviewCore.js —— 由脚本生成（）
   ★ legacy/parts/* 已于 2026-10-14 删除（变更记录），`tools/extract-review-core.mjs` 跑不了了
     ⇒ **本文件即真源**，改提示词直接改这里，别再找 22-script.txt。
   来源：apps/desktop-web/src/legacy/parts/22-script.txt（复盘）＋ 20-script.txt（准则三件）
   生成：node tools/extract-review-core.mjs
   ⚠ **为什么住在 `src/review/` 而不是 `src/data/`**：复盘主流程里有 `fetch` —— 而发布闸门
     `check-s2-guards.mjs` 第 ① 条（网络暴露面）规定 **`src/data/**` 不许出现任何网络原语**
     （fetch / XMLHttpRequest / WebSocket / EventSource）。搬的时候两条闸门都拦过一次（R4 ⇒ RV_SYS 不切、
     ① ⇒ 本文件挪出 data/）——**闸门是对的**，改的是搬法不是闸门。
   规矩：**原样搬运** —— 下面每段都是从源文件**逐字节搬运**搬来的原文（工具每次运行逐字核对）；
        有意改动**只有 1 条**：
     · `reviewCase`：renderSdb(); → notify();

   ⚠ 与 legacy 的共存：legacy 那份同名函数仍在原位（`legacy/**` 一字不动），外壳把
     `window.reviewCase / reviewBlock / ruleBlock / confirmRule` 换成本模块的导出 ⇒ 名字还在旧段里，
     **活得是这一份**（React 抽屉走的就是它；legacy 的 `setFb` 也经记录钩子落到这里）。
   ⚠ 时机（React 侧的必要适配，登记在 Records.jsx）：早期版本是 setFb → renderSdb（**同步重建 DOM**）→ reviewCase
     ⇒ `say()` 一开口就找得到 `#rv-<id>`。React 的 setState 是异步的 ⇒ 抽屉那条路径**等渲染提交后**再跑
     `reviewCase`（见 Records.jsx 的 rvPending effect）；其余行为逐字不变。


*/
/* eslint-disable */

import { RecordsStore } from '../data/recordsStore.js';
/* 体系总纲与判据：只准从包名入口取（禁副本，见 tools/check-isolation.mjs） */
import { SYS, buildContext, buildBlocks, plainAll } from '@xlr/shared-judgment';

/* ⛔ `RV_SYS`（复盘用的系统提示词）**没有切片**：它含 ≥3 个判据特征串（落宫/体用/类象），
   构建第一步的 R4 隔离闸（`tools/check-isolation.mjs`：判据特征串 ≥3 个即判「判据副本」）会直接
   exit 1 ⇒ 提示词**留在 legacy 原地**，这里只**转发**（正文一字不搬、不复制第二份）。 */
const RV_SYS = (typeof window !== 'undefined' && window.RV_SYS) || '';

const SYS_BASE = SYS;

const RV_TASK = '\n\n【复盘任务】\n' +
  '卦主事后确认了实际结果，而这一卦我断错了。请在**上面这套体系内**复盘，不许换体系。\n\n' +
  '规矩：\n' +
  '① 只说本体系内的话；材料里没有的就说没有。\n' +
  '② 必须扣着这一卦的具体信息说（三宫、时辰、类象、五行生克、体用、应期、太极点…），不要讲空泛的道理。\n' +
  '③ 没有把握就直说「这一例信息不足，归不了因」——不许硬凑原因。\n' +
  '④ 别把判据复述一遍当复盘。要指出【具体哪一步推错了】。\n\n' +
  '输出四段（不要表格，不要客套）：\n' +
  '【错在哪】具体到哪一步：落宫吉凶判反了、类象收错了、体用/生克看错、还是应期取错。\n' +
  '【依据】点名本体系的哪一条在起作用（宫性／五行／三宫／体用／应期／太极点／心法），能引原话就引。\n' +
  '【修正】一句话，能当准则记下来，下次遇到同类能照做。\n' +
  '【把握】高／中／低——你对这次归因的把握。\n\n' +
  '最后单独一行，把【修正】压成一条不超过 40 字的准则：\nRULE: ……';

const RV_GUARD = '\n\n【一条硬规矩】\n' +
  '· 不许复述字段名或代号（q、bg、duanci 这种一个都不要出现），直接用「所问」「断辞」「实际结果」说人话；所问不清就当信息不足，不许替卦主补情节。';


/* 读写一律走 ①② 那份（本模块不自己碰 localStorage） */
const loadCases = () => RecordsStore.loadCases();
const loadRules = () => RecordsStore.loadRules();
const saveCases = (a) => RecordsStore.saveCases(a);
const saveRules = (a) => RecordsStore.saveRules(a);
const stamp = () => RecordsStore.stamp();

/* 刷新：源里 `reviewCase` 末尾调 renderSdb() ⇒ 切片里换成 notify()；外壳接到与 renderSdb 重定向同一个口子 */
let notifyHook = null;
export function setNotify(fn) { notifyHook = (typeof fn === 'function') ? fn : null; }
function notify() { try { if (notifyHook) notifyHook(); } catch (e) { } }

/* 出口口径（**照旧走设置那套**）：这几个都是 legacy 26/24-script 的全局 ⇒ 只转发、不重写
   （`loadSet` / `needsKey` / `apiURL` / `hdr` / `payloadOf` / `readSSE` 属设置与出口，本批不动）。
   `sdbTab`（`confirmRule` 末尾切到准则页）同理转发；React 侧再补一次自己的页签切换。 */
const fwd = (nm) => (...a) => (typeof window[nm] === 'function' ? window[nm](...a) : undefined);
const loadSet = fwd('loadSet'), needsKey = fwd('needsKey'), apiURL = fwd('apiURL');
const hdr = fwd('hdr'), payloadOf = fwd('payloadOf'), readSSE = fwd('readSSE'), sdbTab = fwd('sdbTab');
const esc = (s) => (typeof window.esc === 'function' ? window.esc(s) : String(s == null ? '' : s));

function rvFind(id) {
  var a = loadCases();
  for (var i = 0; i < a.length; i++) if (a[i].id === id) return a[i];
  return null;
}
function rvSave(id, html, why, rule) {
  var a = loadCases();
  for (var i = 0; i < a.length; i++) {
    if (a[i].id === id) {
      if (html != null) a[i].rv = html;
      if (why != null) a[i].rvWhy = why;
      if (rule != null) a[i].rvRule = rule;
    }
  }
  saveCases(a);
}
function reviewCase(id) {
  var rec = rvFind(id);
  if (!rec) return;
  var row = document.getElementById('rv-' + id);
  function say(msg, cls) {
    if (!row) row = document.getElementById('rv-' + id);
    if (!row) return;
    row.className = 'rv on';
    var b = row.querySelector('.rv-b'); if (b) b.innerHTML = msg;
    var s = row.querySelector('.rv-s'); if (s) s.textContent = cls || '';
  }

  var s = loadSet();
  var bad = needsKey(s);
  if (bad) {
    say('<span class="rv-no">要复盘得先接一个模型——' + esc(bad) +
        '<br>复盘会把这一卦和实际结果一起交给模型，让它指出错在哪一步。' +
        '<br>（不接模型的话，这一条就只留在记录里，不生成复盘。）</span>', '未接模型');
    return;
  }

  /* 判据整段：跟解卦时同一份（buildContext → buildBlocks → plainAll） */
  var judgeText = '';
  try {
    if (rec.g && rec.g.length >= 3 && rec.zi) {
      var ctx0 = buildContext({
        q: rec.q || '', bg: rec.bg || '', catKey: rec.catKey || 'general', zi: rec.zi,
        g1: rec.g[0], g2: rec.g[1], g3: rec.g[2], src: rec.src || ''
      });
      judgeText = plainAll(ctx0, buildBlocks(ctx0));
    }
  } catch (e0) { judgeText = ''; }

  var usr = '请在这一卦的体系内复盘：\n\n' +
    (judgeText ? '【这一卦的判据 · 与解卦时同一份】\n' + judgeText + '\n' : '') +
    (rec.duanci ? '【我当时给的断辞】\n' + rec.duanci + '\n' : '【我当时给的断辞】（未记）\n') +
    '【实际结果·卦主确认】' + (rec.note || '（卦主未写实际情况）') + '\n\n' +
    (reviewBlock() || '') +
    (ruleBlock() || '') +
    '请按格式复盘。';

  var ep = apiURL();
  say('<span class="cur"></span>', '复盘进行中…');
  var acc = '';
  var t0 = Date.now();

  fetch(ep.url, {
    method: 'POST', headers: hdr(s, ep.proxy),
    body: JSON.stringify(payloadOf(s, ep.proxy,
      [{ role: 'system', content: SYS_BASE + RV_TASK + RV_GUARD }, { role: 'user', content: usr }], true))
  })
    .then(function (resp) {
      if (!resp.ok) return resp.text().then(function (t) { throw new Error('HTTP ' + resp.status + ' ' + String(t).slice(0, 200)); });
      if (!resp.body || !resp.body.getReader) {
        return resp.json().then(function (j) {
          acc = (j.choices && j.choices[0] && j.choices[0].message && j.choices[0].message.content) || '';
        });
      }
      return readSSE(resp, function (d) { acc += d; say(rvFmt(acc) + '<span class="cur"></span>', '复盘进行中…'); });
    })
    .then(function () {
      var sec = ((Date.now() - t0) / 1000).toFixed(1);
      var rule = '', m = acc.match(/RULE[:：]\s*(.+)/);
      if (m) rule = m[1].trim();
      var why = '', m2 = acc.match(/【依据】([\s\S]*?)(?=\n【|$)/);
      if (m2) why = m2[1].trim();
      /* 把 RULE 那一行从展示里去掉（它单独变成准则），其余照排 */
      var show = rvFmt(acc).replace(/\s*RULE[:：][\s\S]*$/, '');
      rvSave(id, show, why, rule);
      say('', '');
      notify();
      var st = document.getElementById('stat');
      if (st) st.textContent = '复盘好了（用时 ' + sec + ' 秒）。看完点「写入准则」——不点就不落库。';
    })
    .catch(function (e) {
      var m3 = String(e && e.message || e);
      say('<span class="rv-no">复盘失败：' + esc(m3) + '</span>' +
          (ep.proxy ? '' : '<br><span class="mini">无法连接。请检查网络设置，或更换供应商。</span>'), '失败');
    });
}
function rvFmt(t) {
  return esc(t).split('\n').map(function (ln) {
    var x = ln.trim();
    if (!x) return '<div style="height:6px"></div>';
    var m = x.match(/^【(.+?)】\s*(.*)$/);
    if (m) return '<div class="rv-t">【' + m[1] + '】</div><div>' + m[2] + '</div>';
    return '<div>' + x + '</div>';
  }).join('');
}
function reviewBlock() {
  var a = loadCases().filter(function (r) { return r.fb; });
  if (!a.length) return '';
  a = a.slice(-6);
  var L = ['【过往复盘 · 只作校准，不要拿它当规则往新卦上套】'];
  a.forEach(function (r) {
    L.push('· ' + r.t + '　' + r.gua + '　问「' + r.q + '」　→ ' +
      (r.fb === '错' ? '我上次断错了' : '上次断对了') +
      (r.note ? '；实际情况：' + r.note : ''));
  });
  L.push('复盘里凡是我上次断错的地方，这次要留意别再犯；但每卦仍以本卦自己的判据为准。');
  L.push('');
  return L.join('\n');
}
function ruleBlock() {
  var rs = loadRules();
  if (!rs.length) return '';
  var L = ['【本门已确认的准则 · 必须遵守】',
           '（这些是卦主本人逐条确认过的修正，来自实断复盘。遇到同类情形按它断，与判据冲突时以它为准。）'];
  rs.forEach(function (r, i) {
    L.push('· 第 ' + (i + 1) + ' 条（' + r.at + (r.gua ? '，' + r.gua : '') + '）：' + String(r.rule || '').replace(/<[^>]+>/g, ''));
    if (r.why) L.push('    依据：' + String(r.why).replace(/<[^>]+>/g, ''));
  });
  L.push('');
  return L.join('\n');
}
function confirmRule(id) {
  var a = loadCases(), rec = null;
  for (var i = 0; i < a.length; i++) if (a[i].id === id) rec = a[i];
  if (!rec || !rec.rv) return;
  var rs = loadRules();
  rs.push({
    id: 'r' + Date.now(), at: stamp(), from: rec.id, gua: rec.gua, q: rec.q,
    rule: rec.rvRule || '', why: rec.rvWhy || ''
  });
  saveRules(rs);
  var st = document.getElementById('stat');
  if (st) st.textContent = '已写入准则（第 ' + rs.length + ' 条）。以后解卦会自动带上它。';
  sdbTab('rules');
}

export const ReviewCore = {
  RV_SYS, rvFind, rvSave, reviewCase, rvFmt, reviewBlock, ruleBlock, confirmRule, setNotify,
};
