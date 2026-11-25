import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { RecordsStore } from '../data/recordsStore.js';

export function RecordGlyph({ kind }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'search' ? <><circle cx="10.8" cy="10.8" r="5.75" /><path d="m15 15 4 4" /></>
      : <path d={{
        back: 'M19 12H5m6-6-6 6 6 6', enter: 'M5 12h14m-6-6 6 6-6 6',
        chevron: 'm9.5 6.5 5.5 5.5-5.5 5.5', close: 'm7 7 10 10m0-10L7 17',
      }[kind]} />}
  </svg>;
}

export default function RecordsTrash({ dead, onBack, onClose, onDialogChange, renderDetail }) {
  const [menu, setMenu] = useState('');
  const [choosing, setChoosing] = useState(false);
  const [selection, setSelection] = useState([]);
  const [confirm, setConfirm] = useState(null);
  const [inspect, setInspect] = useState('');
  const [announcement, announce] = useState('');
  const backRef = useRef(null);
  const bodyRef = useRef(null);
  const cancelRef = useRef(null);
  const menuRef = useRef(null);
  const afterRender = useRef(null);
  const inspectLocation = useRef(0);
  const target = dead.find((r) => r.id === inspect);
  const sorted = [...dead].sort((a, b) => Number(b.deleted) - Number(a.deleted));
  const liveSelection = selection.filter((id) => dead.some((r) => r.id === id));
  const rowNode = (attribute, id) => [...(bodyRef.current?.querySelectorAll('[' + attribute + ']') || [])]
    .find((node) => node.getAttribute(attribute) === id);
  const focus = (node) => node?.focus({ preventScroll: true });
  const after = (callback) => { afterRender.current = callback; };

  useLayoutEffect(() => focus(backRef.current), []);
  useLayoutEffect(() => {
    const callback = afterRender.current;
    afterRender.current = null;
    callback?.();
  });
  useLayoutEffect(() => {
    if (confirm) focus(cancelRef.current);
    onDialogChange(Boolean(confirm));
    return () => onDialogChange(false);
  }, [confirm, onDialogChange]);
  useEffect(() => {
    if (!menu) return;
    menuRef.current?.querySelector('button')?.scrollIntoView?.({ block: 'nearest' });
    const outside = (e) => {
      if (!e.target.closest?.('.rt-more-wrap')) setMenu('');
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [menu]);

  const showDetails = (id) => {
    inspectLocation.current = bodyRef.current?.scrollTop || 0;
    setMenu('');
    setInspect(id);
    after(() => { bodyRef.current.scrollTop = 0; focus(backRef.current); });
  };
  const back = () => {
    if (!target) { onBack(); return; }
    after(() => {
      bodyRef.current.scrollTop = inspectLocation.current;
      focus(rowNode('data-trash-view', target.id) || backRef.current);
    });
    setInspect('');
  };
  const cancel = () => {
    after(() => focus(confirm.row
      ? rowNode('data-trash-more', confirm.row.id) || backRef.current
      : bodyRef.current?.querySelector(confirm.from) || backRef.current));
    setConfirm(null);
  };
  const closeInnermost = () => {
    if (confirm) cancel();
    else if (menu) {
      after(() => focus(rowNode('data-trash-more', menu)));
      setMenu('');
    } else back();
  };
  const prompt = (request) => { setMenu(''); setConfirm(request); };
  const focusNextRow = (id) => {
    const i = sorted.findIndex((r) => r.id === id);
    const next = sorted[i + 1]?.id || sorted[i - 1]?.id;
    after(() => focus(rowNode('data-trash-restore', next) || backRef.current));
  };
  const purge = () => {
    // Effects can update records while a dialog is open: only purge entries still in the trash.
    const deleted = new Set(RecordsStore.loadCasesRaw().filter((r) => r.deleted).map((r) => r.id));
    const ids = confirm.ids.filter((id) => deleted.has(id));
    if (confirm.row) focusNextRow(confirm.row.id);
    else after(() => focus(backRef.current));
    if (ids.length) RecordsStore.purgeMany(ids);
    setConfirm(null);
    setSelection([]);
    setChoosing(false);
    if (ids.length) announce('已永久删除 ' + ids.length + ' 则卦例。');
  };
  const restore = (id, label) => {
    setMenu('');
    focusNextRow(id);
    RecordsStore.restoreCase(id);
    announce('已还原：' + label);
  };
  return <section id="recordTrash" className="rt-layer" data-rt-layer aria-labelledby="recordTrashTitle">
    <div className="rt-header" inert={Boolean(confirm)}>
      <button type="button" ref={backRef} className="rt-back rt-control" data-trash-back onClick={back}>
        <RecordGlyph kind="back" />{target ? '回收站' : '返回记录'}
      </button>
      <button type="button" className="x rt-close" id="sdbClose" aria-label="关闭" onClick={onClose}>×</button>
    </div>
    <div className="rt-content" ref={bodyRef} inert={Boolean(confirm)}>
      {target && <div className="rt-inspect" key={target.id}>
        <h2 id="recordTrashTitle">已删除的卦例</h2>{renderDetail(target)}
      </div>}
      <div className="rt-list-view" hidden={Boolean(target)}>
        <div className="rt-title">
          <div><h2 id={target ? undefined : 'recordTrashTitle'}>回收站</h2>
            <p>{dead.length ? dead.length + ' 则卦例可以还原。' : '删掉的卦例会先放在这里。'}</p>
          </div>
          {dead.length > 0 && <button type="button" className="rt-text rt-control" data-trash-select
            aria-pressed={choosing} onClick={() => { setMenu(''); setChoosing(!choosing); setSelection([]); }}>
            {choosing ? '完成' : '多选'}
          </button>}
        </div>
        {choosing && dead.length > 0 && <div className="rt-batchbar" role="group" aria-label="批量操作">
          <label><input type="checkbox" checked={liveSelection.length === dead.length}
            onChange={() => setSelection(liveSelection.length === dead.length ? [] : dead.map((r) => r.id))} /> 全选</label>
          <span>{liveSelection.length} 则已选</span>
          <button type="button" className="rt-text rt-control" disabled={!liveSelection.length}
            onClick={() => { RecordsStore.restoreMany(liveSelection); announce('已还原 ' + liveSelection.length + ' 则卦例。'); setSelection([]); }}>
            还原
          </button>
          <button type="button" className="rt-text rt-danger rt-control" data-trash-batch-delete disabled={!liveSelection.length}
            onClick={() => prompt({ ids: liveSelection, from: '[data-trash-select]' })}>永久删除</button>
        </div>}
        {!!dead.length ? <ul className="rt-list">{sorted.map((row) => {
          const name = row.q || '未命名卦例';
          return <li className="rt-row" key={row.id}>
            {choosing ? <label className="rt-name">
              <input type="checkbox" aria-label={'选择：' + name} checked={liveSelection.includes(row.id)}
                onChange={() => setSelection((old) => old.includes(row.id) ? old.filter((id) => id !== row.id) : [...old, row.id])} />
              <span>{name}</span>
            </label> : <>
              <button type="button" className="rt-name rt-control" data-trash-view={row.id} onClick={() => showDetails(row.id)}>{name}</button>
              <button type="button" className="rt-text rt-restore rt-control" data-trash-restore={row.id}
                aria-label={'还原：' + name} onClick={() => restore(row.id, name)}>还原</button>
              <div className="rt-more-wrap">
                <button type="button" className="rt-icon rt-control" data-trash-more={row.id}
                  aria-label={'更多操作：' + name} aria-expanded={menu === row.id}
                  onClick={() => setMenu(menu === row.id ? '' : row.id)}>
                  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                    {[5, 12, 19].map((x) => <circle key={x} cx={x} cy="12" r="1.5" />)}
                  </svg>
                </button>
                {menu === row.id && <div className="rt-menu" ref={menuRef} data-trash-menu>
                  <button type="button" className="rt-danger rt-control" data-trash-delete={row.id}
                    onClick={() => prompt({ ids: [row.id], row })}>永久删除</button>
                </div>}
              </div>
            </>}
          </li>;
        })}</ul> : <div className="rt-empty">回收站是空的</div>}
        {!!dead.length && !choosing && <div className="rt-bottom">
          <button type="button" className="rt-text rt-danger rt-control" data-trash-clear
            onClick={() => prompt({ ids: dead.map((r) => r.id), from: '[data-trash-clear]', all: true })}>清空回收站</button>
        </div>}
      </div>
    </div>
    <span className="rt-announcement" role="status">{announcement}</span>
    <button type="button" hidden data-trash-esc onClick={closeInnermost} />
    {confirm && <div className="rt-overlay">
      <div className="rt-confirm" role="alertdialog" aria-modal="true" aria-labelledby="rtConfirmTitle" aria-describedby="rtConfirmCopy"
        onKeyDown={(e) => {
          if (e.key !== 'Tab') return;
          const list = [...e.currentTarget.querySelectorAll('button')];
          const i = list.indexOf(document.activeElement);
          if ((e.shiftKey && i <= 0) || (!e.shiftKey && i === list.length - 1)) {
            e.preventDefault(); focus(list[e.shiftKey ? list.length - 1 : 0]);
          }
        }}>
        <h3 id="rtConfirmTitle">{confirm.all ? '清空回收站？' : '永久删除卦例？'}</h3>
        {confirm.row && <p className="rt-confirm-item">{confirm.row.q || '未命名卦例'}</p>}
        <p id="rtConfirmCopy">{confirm.row ? '删除后，这则卦例无法还原。'
          : '这 ' + confirm.ids.length + ' 则卦例将被永久删除，无法还原。'}</p>
        <div className="rt-confirm-actions">
          <button type="button" className="rt-control" ref={cancelRef} data-trash-cancel onClick={cancel}>取消</button>
          <button type="button" className="rt-control rt-confirm-delete" data-trash-confirm onClick={purge}>永久删除</button>
        </div>
      </div>
    </div>}
  </section>;
}
