/* Records.jsx —— /：记录抽屉（`#sdb`）的 React 版
 *
 * 照抄对象（逐字）：`apps/desktop-web/src/legacy/parts/22-script.txt` **L132–L156**（DOM 结构）
 *   + `20-script.txt` 的渲染函数（`renderCases` L292–360 · `caseDetailHtml` L362–407 ·
 *     `rvHtml` L409–424 · `sdbFootHtml` L162–191 · `renderRules` L426–450）。
 *
 * 分工（见 早期实现记录 §39.2/§39.6）：
 *   · **读/写存储**：走 React 侧的 `data/recordsStore.js`（**① 2026-10-13 搬来**，是 `20-script.txt`
 *     L9–122 的逐字切片；外壳同时把 `window.load*`/`save*`/动词收到同一份上 ⇒ 全应用一份真相）
 *   · **复盘 / 准则**：走 React 侧的 `data/reviewCore.js`（**③ 2026-10-13 搬来**，是 22-script 的
 *     复盘主流程 + 20-script 的 `reviewBlock`/`ruleBlock`/`confirmRule` 逐字切片）。
 *   · **照抄** 渲染与接线（legacy 那批自己写 `innerHTML`，不能复用）
 *   · `renderSdb` 由外壳**重定向**到本组件的 `refresh()`（登记见 §39.6.4；受 `VITE_S2_RECORDS` 控制）
 *
 * 改版：卦例列表只显示所问；资料和反馈移入详情，回收站使用独立子页。
 *
 * ===== Step 4：交互（本地 16 项；出网 3 项按 §39.4 / §十三① **留待**，按钮 DOM 保留、功能不接）=====
 * 逐条对应早期版本监听器（源行号 = `20-script.txt` / `22-script.txt`，均逐字照抄语义）：
 *   · 关：`#sdbClose` / `#sdbMask`（22 L159-160）· Esc（22 L170，早期版本就绑在 `#sdb` 上）
 *   · 标签 `.stab`（22 L167-169 → `sdbTab` L138-147）
 *   · 搜索 `#sdbQ`（22 L162-166；早期版本写 `window.__sdbQ`，这边**镜像**着写）
 *   · 行点击→详情 / `[data-back]`（`bindCaseBox` L454-467）
 *   · 详情内 `[data-fb]`（L468-470 → `setFb`）· `[data-del]`（L471-473）
 *   · `[data-note]`（L474-478，**原生 `change`**，不是 `input`）
 *   · `[data-okrule]`（L479-481 → `confirmRule`，末尾照抄 `sdbTab('rules')` L533）
 *   · `[data-norule]`（L482-488 **逐字照抄**）· `[data-delrule]`（`renderRules` L447-449 → `delRule`）
 *   · 底栏：回收站入口、两段式清空记录；回收站自己的操作在 RecordsTrash。
 * 抽屉事件委托到根节点；卦例列表不再有内联删除，详情保留原来的存储动作。
 * 出网三条的现状（③ 更新）：
 *   ① `[data-fb=错]` 的复盘 —— **已接**（③：跑 `data/reviewCore.js` 的 `reviewCase`，出口口径照旧走设置那套）
 *   ③ 复盘的流式渲染（`RV_SYS` + `readSSE`）—— **已接**（同一份切片）
 *   ② `[data-ask]`（接着问）—— **仍未接**：早期版本由 `26-script.txt` L762-763 的 document 委托 → `openRecAsk`
 *      出网追问；外壳在捕获阶段吞掉这次点击（登记 **变更记录**），本批不动。
 */
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { RecordsStore } from '../data/recordsStore.js';
import SafeZone from './SafeZone.jsx';
import RecordsTrash, { RecordGlyph } from './RecordsTrash.jsx';
import windowsRecordsCss from '../styles/windowsRecords.css?inline';
/* 桌面壳的组装链只带 shell.js；CSS 随组件内联而非生成独立文件。 */
import { ReviewCore } from '../review/reviewCore.js';
import { recAskHtml } from '../data/modelAskCore.js';   /* ★ 记录行「接着问」区（原先只读 window.recAskHtml ⇒ 渲染成空） */
import { htmlEsc as esc } from '../data/docRender.js';   /* ★ 收敛到唯一一份转义实现（原先降级成不转义的 String()） */

/* legacy 全局取值（装载顺序：classic 脚本段在前、外壳 module 在后 ⇒ 渲染期一定已在） */
const g = (k) => (typeof window !== 'undefined' ? window[k] : undefined);
/* ★ `esc` 不再本地降级——已改为上面从 docRender.js 导入的唯一一份 `htmlEsc`。 */

/* 上游的详情断辞回退；当前桌面壳不一定提供 window.renderDoc。 */
const htmlEsc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[m]);
const boldStr = (s) => String(s == null ? '' : s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
const renderDoc = (t) => {
  if (typeof g('renderDoc') === 'function') return g('renderDoc')(t);
  let out = '';
  htmlEsc(t).split('\n').forEach((ln) => {
    const s = ln.trim();
    if (!s) { out += '<div style="height:7px"></div>'; return; }
    const m = s.match(/^【(.+?)】\s*(.*)$/);
    if (m) {
      out += '<h4>【' + m[1] + '】</h4>';
      if (m[2]) out += '<div>' + boldStr(m[2]) + '</div>';
      return;
    }
    out += '<div>' + boldStr(s) + '</div>';
  });
  return out;
};

/* 读数据（①：改走 React 侧存储层；语义与 legacy 同名函数逐字相同 —— `loadCases()` = 未删的，
   `loadCasesRaw()` = 全量含回收站） */
const readCases = () => { try { return RecordsStore.loadCases() || []; } catch (e) { return []; } };
const readCasesRaw = () => { try { return RecordsStore.loadCasesRaw() || []; } catch (e) { return []; } };
const readRules = () => { try { return RecordsStore.loadRules() || []; } catch (e) { return []; } };

/* `sdbDayOf`（20-script.txt L282–291）：'2026-10-09 14:32' → 今天/昨天/7 天内/更早 + MM-DD */
function dayOf(t) {
  const s = String(t || '').slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return { k: '更早', md: '' };
  const d = new Date(s + 'T00:00:00');
  const now = new Date();
  const t0 = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diff = Math.round((t0.getTime() - d.getTime()) / 86400000);
  return { k: diff <= 0 ? '今天' : diff === 1 ? '昨天' : diff <= 7 ? '7 天内' : '更早', md: s.slice(5) };
}

/* 底部为两个平级动作；回收站内容在抽屉自己的子页。 */
function Foot({ n, deadCount, armed, onTrashEnter }) {
  return <div className="sdb-foot">
    <button type="button" className="rt-foot-action" data-clear="1" disabled={!n}
      data-armed={armed ? '1' : undefined}>
      <span aria-live="polite">{armed ? '确认将全部记录移入回收站' : '清空记录'}</span>
    </button>
    <button type="button" className="rt-foot-action" data-trash-enter
      aria-controls="recordTrash" onClick={onTrashEnter}>
      <span>回收站</span><span className="rt-entry-count">{deadCount} 则</span><RecordGlyph kind="enter" />
    </button>
  </div>;
}

/* 卦例列表仅显示所问；反馈、实际情况、断辞、追问与删除留在详情。 */
function CaseRow({ r }) {
  return (
    <button type="button" className="case-row case-row-question" data-row={r.id}>
      <span className="cr-q">{String(r.q ?? '') || '未命名卦例'}</span>
      <span className="cr-enter" aria-hidden="true"><RecordGlyph kind="chevron" /></span>
    </button>
  );
}

/* 复盘区（`rvHtml` L409–424）：本批**不出网** ⇒ `!r.rv` 时停在既有「准备中…」分支（登记见 §39.4） */
function Rv({ r }) {
  if (!r.rv) {
    return (
      <div className="rv on" id={'rv-' + r.id}><div className="rv-h">复盘 · <span className="rv-s"><span className="cur" /></span></div>
        <div className="rv-b" /></div>
    );
  }
  return (
    <div className="rv on" id={'rv-' + r.id}>
      <div className="rv-h">复盘 · <span className="rv-s">待你确认</span></div>
      <div className="rv-b" dangerouslySetInnerHTML={{ __html: r.rv }} />
      <div className="rv-f">
        <button type="button" className="btn pri sm" data-okrule={r.id}>写入准则</button>
        <button type="button" className="btn sm" data-norule={r.id}>算了，不记</button>
        <span className="mini">写入后，以后每次解卦都会一并交给模型</span>
      </div>
    </div>
  );
}

/* 空态文案（`renderCases` L307–310，逐字） */
function EmptyCases() {
  return (
    <div className="mini" style={{ padding: '16px 18px' }}>还没有记录。<br />每起一卦都会自动记进这里。事后回来标一下准不准——<b>错的卦才值钱</b>。<br /><br />标了「没应验」，这里会出现复盘：它得说清凭什么、错在哪、怎么修，你点「写入准则」才算数。</div>
  );
}

/* 一条准则（`renderRules` L437–444）
   跟记录条一个口径：列表只出关键句，依据/来源点开再看。
   设计语言不变：还是 rr-h / rr-b / rr-w 这套，只是默认收起。 */
function RuleRow({ r }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={'rule-row' + (open ? ' open' : '')}>
      <div className="rr-h"><span className="rr-t">{esc(r.at)}</span>
        <span className="cr-g">{esc(r.gua || '')}</span>
        <span className="sp" /><button type="button" className="btn sm" data-delrule={r.id}>删</button></div>
      <div className="rr-b"
           onClick={() => setOpen(!open)}
           style={open ? undefined : { display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}
           dangerouslySetInnerHTML={{ __html: r.rule || '' }} />
      {open && r.why ? <div className="rr-w" dangerouslySetInnerHTML={{ __html: '依据：' + r.why }} /> : null}
      {open && r.q ? <div className="mini">{'来自卦例：' + esc(r.q)}</div> : null}
      <div className="mini" style={{ cursor: 'pointer', opacity: 0.75 }} onClick={() => setOpen(!open)}>
        {open ? '收起 ›' : '展开看依据与来源 ›'}
      </div>
    </div>
  );
}

export default function Records({ open = false, onClose }) {
  const [cases, setCases] = useState(() => readCases());
  const [dead, setDead] = useState(() => readCasesRaw().filter((r) => r.deleted));
  const [rules, setRules] = useState(() => readRules());
  const [tab, setTab] = useState('cases');
  const [q, setQ] = useState(() => String((typeof window !== 'undefined' && window.__sdbQ) || ''));
  const [detailId, setDetailId] = useState('');   /* ⇔ 早期版本 `window.__sdbDetail`（bindCaseBox L458 / L464-465） */
  const [trashOpen, setTrashOpen] = useState(false);
  const [trashReturning, setTrashReturning] = useState(false);
  const [trashDialog, setTrashDialog] = useState(false);
  const trashLocation = useRef(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const searchInputRef = useRef(null);
  const searchToggleRef = useRef(null);
  const nextSearchFocus = useRef(null);
  const [armed, setArmed] = useState(false);      /* ⇔ 早期版本 `[data-clear]` 的 `data-armed`（L270-277） */
  /* ★ ③：待复盘的记录 id —— 点「没应验」后**不能当场调** `reviewCase`（React 的 setState 是异步的，
     那一刻 `#rv-<id>` 还没渲染出来；早期版本是 setFb → renderSdb 同步重建 DOM 后再调）。
     所以先记下 id，等下面那个 effect（跑在渲染提交之后）再跑 ⇒ 与早期版本的先后顺序一致。 */
  const [rvPending, setRvPending] = useState('');
  const alive = useRef(true);
  const rootRef = useRef(null);
  const scrollRef = useRef(null);
  const listLocation = useRef(null);
  const returnToList = useRef(null);
  const detailIdRef = useRef('');                 /* 给 `refresh()` 用（那个 effect 的 deps 是 []，读不到最新 state） */
  const footSnap = useRef(0);
  const everOnRef = useRef(false);                /* 抽屉**开过一次**之后，关闭态的 `class` 属性要**留着空串**（见渲染处注释） */
  useEffect(() => { detailIdRef.current = detailId; }, [detailId]);

  // 用同一滚动容器打开详情；返回时恢复列表的滚动与键盘焦点。
  useLayoutEffect(() => {
    const scroller = scrollRef.current;
    if (!open || tab !== 'cases' || !scroller) return;
    if (detailId) {
      scroller.scrollTop = 0;
      scroller.querySelector('[data-back]')?.focus({ preventScroll: true });
      return;
    }
    const previous = returnToList.current;
    if (!previous) return;
    returnToList.current = null;
    scroller.scrollTop = previous.top;
    const row = Array.from(scroller.querySelectorAll('.case-row-question[data-row]'))
      .find((item) => item.dataset.row === previous.id);
    row?.focus({ preventScroll: true });
  }, [open, tab, detailId]);

  const changeQuery = (value) => {
    setArmed(false);
    window.__sdbQ = value.trim();
    setQ(value);
  };
  const toggleSearch = () => {
    setArmed(false);
    nextSearchFocus.current = searchOpen ? searchToggleRef : searchInputRef;
    setSearchOpen(!searchOpen);
  };
  useLayoutEffect(() => {
    nextSearchFocus.current?.current?.focus({ preventScroll: true });
    nextSearchFocus.current = null;
  }, [searchOpen]);

  const enterTrash = (event) => {
    trashLocation.current = { trigger: event.currentTarget, top: scrollRef.current?.scrollTop || 0 };
    setArmed(false);
    setTrashReturning(false);
    setTrashOpen(true);
  };
  const leaveTrash = () => { setTrashReturning(true); setTrashOpen(false); };
  useLayoutEffect(() => {
    if (trashOpen || !trashReturning || !trashLocation.current) return;
    const location = trashLocation.current;
    trashLocation.current = null;
    if (scrollRef.current) scrollRef.current.scrollTop = location.top;
    location.trigger.focus({ preventScroll: true });
  }, [trashOpen, trashReturning]);
  useEffect(() => {
    if (open) return;
    setArmed(false);
    setTrashOpen(false);
    setTrashReturning(false);
    setTrashDialog(false);
    trashLocation.current = null;
  }, [open]);

  const delNow = (id) => { RecordsStore.delCase(id); };   /* L101 `delCase` = markCase(id,true)；A4② 起走 store */
  const close = () => { if (typeof onClose === 'function') onClose(); };                   /* `sdbClose` L133-137 的 React 等价物（外壳摘 `.on` + `sdb-lock`） */

  /* 唯一刷新源（§39.6.5）：重读 legacy 数据 ⇒ `#sdbN`/`#ruleN` 两个计数与两个列表全部由 state 派生。
     外壳把 legacy 的 `window.renderSdb` 重定向到这里（§39.6.4）；未注册时 legacy 侧静默早退。 */
  useEffect(() => {
    alive.current = true;
    const refresh = () => {
      if (!alive.current) return;
      setCases(readCases());
      setDead(readCasesRaw().filter((r) => r.deleted));
      setRules(readRules());
      if (!detailIdRef.current) setArmed(false);
    };
    window.__xlrRecordsRefresh = refresh;
    refresh();
    return () => {
      alive.current = false;
      if (window.__xlrRecordsRefresh === refresh) delete window.__xlrRecordsRefresh;
    };
  }, []);

  /* 打开抽屉：早期版本入口是 `sdbOpen('cases')`（22-script L177）⇒ `sdbTab('cases')` + 末尾一次 `renderSdb()`（20 L126-132）*/
  useEffect(() => {
    if (!open) return;
    setTab('cases');
    setCases(readCases());
    setDead(readCasesRaw().filter((r) => r.deleted));
    setRules(readRules());
  }, [open]);

  /* `[data-clear]` 第二段的 4 秒回退（L272-277 的 setTimeout 4000） */
  useEffect(() => {
    if (!armed) return;
    const t = setTimeout(() => setArmed(false), 4000);
    return () => clearTimeout(t);
  }, [armed]);

  /* `[data-note]`：早期版本绑的是**原生 change**（`bindCaseBox` L474-478），不是 input。
     ⇒ 这边也用原生 change（React 的 `onChange` 映射到 input 事件，语义不同，不能拿来顶替）。 */
  useEffect(() => {
    const el = rootRef.current;
    if (!el) return;
    const onChange = (ev) => {
      const t = ev.target;
      if (!t || typeof t.closest !== 'function') return;
      const n = t.closest('[data-note]');
      if (!n) return;
      const v = String(n.value || '').trim();
      RecordsStore.setNote(n.getAttribute('data-note'), v);   /* A4②：动词走 store（原来 g('setNote')） */
      /* 早期版本紧接着还有一句 `renderSdb()`；`setNote` → `saveCases` → `renderSdb`（已被重定向到 refresh）⇒ 不必再调。
         ⚠ 但早期版本那次重渲染会把整行 innerHTML **重建** ⇒ 输入框的显示值也被归一成存档里的 trim 结果；
           React 复用同一个节点、不会回写未受控输入的 live value ⇒ 这里照早期版本那样补一次回写。 */
      if (n.value !== v) n.value = v;
    };
    el.addEventListener('change', onChange);
    return () => el.removeEventListener('change', onChange);
  }, []);

  /* 详情态失效：早期版本 `renderCases` L297-305 —— 详情那条取自 `recById()`（= `loadCases()`，不含已删）
     ⇒ 一旦它被删，就清空详情态、回列表。 */
  useEffect(() => {
    if (!detailId || cases.some((r) => r.id === detailId)) return;
    returnToList.current = listLocation.current;
    listLocation.current = null;
    setDetailId('');
  }, [detailId, cases]);

  /* ★ ③ 复盘主流程（**会出网**）：跑在**渲染提交之后** ⇒ 那一刻 `#rv-<id>`（含 `.rv-b`/`.rv-s`）
     已经由上面的 `<Rv>` 渲染出来，`reviewCase` 里的 `say()` 才有地方落笔（与早期版本「先重建 DOM 再复盘」同序）。
     ⚠ 只在这里跑一次；`reviewCase` 自己会判「没接模型就只提示」「已复盘过就不再跑」。 */
  useEffect(() => {
    if (!rvPending) return;
    const id = rvPending;
    setRvPending('');
    ReviewCore.reviewCase(id);
  }, [rvPending]);

  /* `[data-fb]`（L468-470 → `setFb` L84-95）。
     ★ ③（）：**复盘接上了**。早期版本「没应验」会顺手触发 `reviewCase()`（出网，20 L94 →
     22-script L43 的复盘主流程）。这里照早期版本语义走，只把**时机**改成「等渲染提交之后再跑」：
       ① 先在 setFb 里把复盘钩子**临时挂空** ⇒ 只做本地标记（等价于早期版本 setFb 里那一步 saveCases）；
       ② 记下 `rvPending`，由上面的 effect 在 `#rv-<id>` 渲染出来之后跑 `reviewCase`（早期版本是先
          renderSdb 同步重建 DOM 再调 ⇒ 先后顺序一致）。
     条件与早期版本逐字相同：`fb === '错'` 且这一条**还没复盘过**（`!rec.rv`）才跑。
     ★ 仍然**只有一份** setFb / reviewCase 实现（不复制数据逻辑）。 */
  const markFb = (id, fb) => {
    if (fb !== '错') { RecordsStore.setFb(id, fb); return; }        /* 「应验」不碰复盘（L94 条件不成立） */
    const prev = RecordsStore.setReviewHook(null);
    try { RecordsStore.setFb(id, fb); } finally { RecordsStore.setReviewHook(prev); }
    const rec = readCases().find((r) => r.id === id);
    if (rec && !rec.rv) setRvPending(id);                          /* ⇔ 早期版本 L94 的条件 */
  };

  /* `[data-norule]`（L482-488 照抄：把该条的复盘清掉。早期版本这段是内联的，没有对应的全局函数）
     ★★ **有意行为变更（登记 变更记录）**：早期版本这里取的是 `loadCases()`（= **不含已删**）再 `saveCases()`
     ⇒ **写一次就把回收站里那些已删记录永久写没**。本组件改为取 `loadCasesRaw()`（含已删），
     其余逐字照抄（`rv=''` / `rvWhy=''` 两处清空语义不变）。
     ⚠ 同族另 4 处在 legacy 段里（`setFb`/`setNote`/`updateCaseDuanci`/`markModelAnswered` + `addCase`），
       那些是**只读照抄对象**不能改 ⇒ 由外壳 `mountRecords()` 的写入者包装统一兜住（见 main.jsx 变更记录 注释）。 */
  const noRule = (id) => {
    const a = RecordsStore.loadCasesRaw();   /* A4①：读写都直接走存储层（原来是 g('loadCasesRaw')/g('saveCases')） */
    for (let i = 0; i < a.length; i++) if (a[i].id === id) { a[i].rv = ''; a[i].rvWhy = ''; }
    RecordsStore.saveCases(a);
  };

  /* `[data-okrule]`（L479-481 → `confirmRule`）：早期版本末尾还会 `sdbTab('rules')`（L533）⇒ 这边同步切页 */
  const confirmRuleById = (id) => {
    ReviewCore.confirmRule(id);   /* A4③：准则那条线走 React 侧切片（原来 g('confirmRule')） */
    changeTab('rules');
  };

  const changeTab = (next) => {
    setArmed(false);
    setTab(next);
  };

  /* 抽屉主视图的事件委托：详情动作先判，卦例列表按钮只进入详情。 */
  const onClick = (e) => {
    const t = e.target;
    if (!t || typeof t.closest !== 'function') return;
    let b;
    /* ① 行内按钮（`bindCaseBox`） */
    if ((b = t.closest('[data-back]'))) {                                                                   /* L462-467 */
      setArmed(false);
      if (detailId) {
        returnToList.current = listLocation.current;
        listLocation.current = null;
      }
      setDetailId('');
      return;
    }
    if ((b = t.closest('[data-fb]'))) { markFb(b.getAttribute('data-id'), b.getAttribute('data-fb')); return; }  /* L468-470 */
    if ((b = t.closest('[data-del]'))) { delNow(b.getAttribute('data-del')); return; }                      /* L471-473（详情态走这条） */
    if ((b = t.closest('[data-okrule]'))) { confirmRuleById(b.getAttribute('data-okrule')); return; }        /* L479-481 */
    if ((b = t.closest('[data-norule]'))) { noRule(b.getAttribute('data-norule')); return; }                /* L482-488 */
    if ((b = t.closest('[data-delrule]'))) {                                                                /* `renderRules` L447-449 */
      RecordsStore.delRule(b.getAttribute('data-delrule')); return;
    }
    /* ② 清空记录保持原有的软删除，只在第二次确认后移入回收站。 */
    if ((b = t.closest('[data-clear]'))) {                                                                  /* L267-279 */
      if (b.disabled || b.hasAttribute('disabled')) return;
      if (armed) { setArmed(false); RecordsStore.clearCases(); return; }
      setArmed(true);
      return;
    }
    /* 原生按钮：整行点击和键盘 Enter/Space 都只打开详情。 */
    if ((b = t.closest('.case-row-question[data-row]'))) {
      setArmed(false);
      const id = b.dataset.row;
      listLocation.current = { id, top: scrollRef.current?.scrollTop ?? 0 };
      setDetailId(id);
    }
  };

  const done = cases.filter((r) => r.fb).length;
  const kw = String(q || '').trim().toLowerCase();
  const rows = cases.slice().sort((x, y) => String(y.t).localeCompare(String(x.t))).filter((r) => {
    if (!r || typeof r !== 'object') return false;
    if (!kw) return true;
    return ((r.q || '') + ' ' + (r.gua || '') + ' ' + (r.note || '') + ' ' + (r.cat || '')).toLowerCase().indexOf(kw) >= 0;
  });

  const detail = detailId ? cases.find((r) => r.id === detailId) : null;
  const listFootN = rows.length;
  if (!detail) footSnap.current = listFootN;

  /* 关闭态的 `class` 属性有两种形态，取决于"开没开过"：
     · 从没开过 ⇒ **没有 class 属性**（`mk()` 建节点时只设了 id，22-script L130）
     · 开过一次又关掉 ⇒ **有 `class=""`**（`sdbClose()` 是 `el.classList.remove('on')`，20-script L135；
       摘掉最后一个类之后属性保留、值为空串）
     ★ Step 2 的静态比对看到的是第一种（所以当时把 `className` 改成"开时才给"）；本条把第二种也复现。
       只读地记一个"开过"标志（写 ref 幂等、不影响渲染结果）。 */
  if (open) everOnRef.current = true;
  const rootCls = open ? { className: 'on' } : (everOnRef.current ? { className: '' } : {});

  return (
    <div id="sdb" data-tab={tab} ref={rootRef} onClick={onClick}
      onKeyDown={(e) => {
        if (e.key !== 'Escape') return;
        const layer = rootRef.current?.querySelector('[data-trash-esc]')
          || rootRef.current?.querySelector('[data-search-esc]');
        if (layer) { e.preventDefault(); e.stopPropagation(); layer.click(); }
        else close();
      }}
      {...rootCls}>
      <div className="sdb-mask" id="sdbMask" onClick={() => { if (!trashDialog) close(); }} />
      <aside className="sdb-p">
      <div className={'rt-main' + (trashReturning ? ' rt-returning' : '')} hidden={trashOpen}>
        <div className="sdb-h rt-main-header" data-search-open={tab === 'cases' && searchOpen ? 'true' : 'false'}>
          <b>记录</b><span className="sp" />
          <span className="n" id="sdbN">{cases.length + ' 则' + (done ? '（已标 ' + done + '）' : '')}</span>
          {tab === 'cases' && <div className="rt-search-inline">
            <div className="rt-search-field" hidden={!searchOpen}>
              <input ref={searchInputRef} id="sdbQ" type="search" value={q}
                placeholder="搜索卦例" aria-label="搜索卦例" onChange={(e) => changeQuery(e.currentTarget.value)} />
              {!!q && <button type="button" className="rt-search-clear rt-control" aria-label="清除搜索"
                onClick={() => { changeQuery(''); searchInputRef.current?.focus(); }}><RecordGlyph kind="close" /></button>}
            </div>
            <button type="button" ref={searchToggleRef} className="rt-control rt-search-toggle"
              data-search-esc={!trashOpen && searchOpen ? 'true' : undefined}
              data-filtered={Boolean(kw)} aria-controls="sdbQ" aria-expanded={searchOpen}
              aria-label={searchOpen ? '收起搜索' : kw ? '展开搜索，筛选中' : '搜索卦例'} onClick={toggleSearch}>
              <RecordGlyph kind={searchOpen ? 'chevron' : 'search'} />
            </button>
          </div>}
          <button className="x rt-control rt-main-close" id={trashOpen ? undefined : 'sdbClose'}
            type="button" aria-label="关闭记录" onClick={close}><RecordGlyph kind="close" /></button>
        </div>
        <div className="sdb-t">
          <button className={'stab' + (tab === 'cases' ? ' on' : '')} type="button" data-tab="cases" aria-pressed={tab === 'cases'} onClick={() => changeTab('cases')}>卦例</button>
          <button className={'stab' + (tab === 'rules' ? ' on' : '')} type="button" data-tab="rules" aria-pressed={tab === 'rules'} onClick={() => changeTab('rules')}>准则 <b id="ruleN">{rules.length}</b></button>
        </div>
        <div className="sdb-b" ref={scrollRef}>
          <div id="sdbCases" hidden={tab !== 'cases'}>
            {/* ★ 抽屉里的内容套一层 SafeZone —— 数据出问题时**只废掉这一块**，
                抽屉壳（标题/页签/底栏/遮罩）照旧在，里面显示一句「记录出错了」而不是整片失灵。 */}
            <SafeZone what="记录">
              {detail ? <Detail r={detail} /> : (cases.length
                ? (rows.length
                  ? <CaseList rows={rows} />
                  : <div className="sdb-empty2">没有匹配的记录。</div>)
                : <EmptyCases />)}
            </SafeZone>
          </div>
          <div id="sdbRules" hidden={tab !== 'rules'}>
            <SafeZone what="准则">
              {rules.length
                ? <div className="rt-record-list">{rules.slice().reverse().map((r) => <RuleRow r={r} key={r.id} />)}</div>
                : <div className="mini" style={{ padding: '16px 18px' }}>还没有准则。<br />准则只从【你确认过的复盘】里来——不自动写、不偷偷学。</div>}
            </SafeZone>
          </div>
                  </div>
        <Foot n={detail ? footSnap.current : listFootN} deadCount={dead.length} armed={armed} onTrashEnter={enterTrash} />
        <div className="sdb-f">记下来 → 标准不准 → 没应验就复盘 → <b>你确认了才写进准则</b></div>
      </div>
      {trashOpen && <RecordsTrash dead={dead} onBack={leaveTrash} onClose={close}
        onDialogChange={setTrashDialog} renderDetail={(record) => <Detail r={record} readOnly />} />}
      </aside>
      <style>{windowsRecordsCss}</style>
    </div>
  );
}

/* 分组 + 条目（`renderCases` L328–357 的分组分支；**底栏不在这里** —— 见上面"底栏的位置"注释） */
function CaseList({ rows }) {
  let curGrp = '';
  const out = [];
  rows.forEach((r, idx) => {
    const dd = dayOf(r.t);
    if (dd.k !== curGrp) { curGrp = dd.k; out.push(<div className="sdb-grp" key={'g' + idx}>{curGrp}</div>); }
    out.push(<CaseRow r={r} key={r.id} />);
  });
  return <div className="rt-record-list">{out}</div>;
}

/* 复盘详情（`caseDetailHtml` L365–406，逐字） */
function Detail({ r, readOnly = false }) {
  const rd = (h) => (h ? <div className="cd-d" dangerouslySetInnerHTML={{ __html: renderDoc(String(h)) }} /> : null);
  return (
    <div className="cd">
      {!readOnly && <div className="cd-bar"><button type="button" className="btn sm rt-detail-back" data-back="1">
        <RecordGlyph kind="back" /> 返回列表</button>
        <span className="sp" /><span className="mini">复盘</span></div>}
      <div className="cd-sec"><div className="cd-k">所问</div><div className="cd-q">{String(r.q ?? '')}</div></div>
      {r.bg ? <div className="cd-sec"><div className="cd-k">背景</div><div className="cd-q">{esc(r.bg)}</div></div> : null}
      <div className="cd-sec"><div className="cd-k">起卦</div><div className="cd-gua">{esc(r.gua || '')}</div>
        <div className="mini">{'事类：' + esc(r.cat || '通用') + ' · 记于 ' + esc(r.t || '')}</div></div>
      <div className="cd-sec"><div className="cd-k">当时的断辞</div>
        {r.duanci ? rd(r.duanci) : <div className="mini">这一卦没出过断辞。</div>}</div>
      {Array.isArray(r.thread) && r.thread.length ? (
        <div className="cd-sec"><div className="cd-k">{'在这卦里的追问（' + r.thread.length + '）'}</div>
          {r.thread.map((it, i) => (it ? (
            <div className="ask-item" key={i}><div className="ask-q">{esc(it.q)}</div>
              <div className="ask-a" dangerouslySetInnerHTML={{ __html: renderDoc(it.a || '') }} /></div>
          ) : null))}
        </div>
      ) : null}
      {readOnly && r.note ? <div className="cd-sec"><div className="cd-k">实际情况</div><div className="cd-q">{r.note}</div></div> : null}
      {!readOnly && <>
        <div className="cd-sec"><div className="cd-k">准不准 · 实际情况</div>
          <div className="cr-a" style={{ marginBottom: '8px' }}>
            <button type="button" className={'btn sm' + (r.fb === '对' ? ' on' : '')} data-fb="对" data-id={esc(r.id)}>应验</button>
            <button type="button" className={'btn sm dang' + (r.fb === '错' ? ' on' : '')} data-fb="错" data-id={esc(r.id)}>没应验</button>
            <span className="sp" />
            <button type="button" className="btn sm" data-del={esc(r.id)}>删掉这则</button>
          </div>
          <div className="cr-note"><span>实际情况</span>
            <input type="text" data-note={esc(r.id)} defaultValue={r.note} placeholder="后来到底怎么着了？写一句，复盘要用它" /></div>
        </div>
        {r.fb === '错' ? <div style={{ padding: '0' }}><Rv r={r} /></div> : null}
        {r.ans === 'model'
          ? <div className="cd-sec"><div className="cd-k">接着问（还是这一卦）</div>
              <div className="cr-askbox" data-askbox={esc(r.id)} dangerouslySetInnerHTML={{ __html: recAskHtml(r) }} /></div>
          : <div className="cd-sec"><div className="cd-k">接着问</div>
              <div className="mini">用模型解过这一卦之后，这里就能接着问。</div></div>}
      </>}
    </div>
  );
}
