import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { RecordsStore } from '../data/recordsStore.js';
import {
  getHuiguanReminded, markHuiguanReminded, pickHuiguanReminder,
  HUIGUAN_CARD_MS, HUIGUAN_GUIDE_MS,
} from '../data/huiguanReminder.js';
import reminderCss from '../styles/huiguanReminder.css?inline';

const busyLayer = '#xlrSplash:not(.out), #onbLayer.on, #setScreen.on, #sdb.on, #mask.on, .skinmenu, dialog[open]';
function mayShow() {
  if (document.visibilityState === 'hidden') return false;
  const active = document.activeElement;
  if (active?.matches('textarea, input:not([type="button"]), [contenteditable="true"]') &&
      String(active.value ?? active.textContent ?? '').trim()) return false;
  return ![...document.querySelectorAll(busyLayer)].some(el => {
    const style = getComputedStyle(el);
    return style.display !== 'none' && style.visibility !== 'hidden';
  });
}

// Never stack two notifications. Once per foreground visit, with independent
// week/month keys so the other report can appear on the next visit if still due.
let offeredThisVisit = false;

export default function HuiguanReminder({ onOpen }) {
  const active = useRef(null);
  const progress = useRef(null);
  const [flow, setFlow] = useState(null);
  const [allowed, setAllowed] = useState(false);
  const show = (period, guide = false) => {
    const next = period ? { period, guide, remaining: guide ? HUIGUAN_GUIDE_MS : HUIGUAN_CARD_MS } : null;
    active.current = next;
    setFlow(next);
  };

  useEffect(() => {
    let queued = 0;
    const check = () => {
      const available = mayShow();
      setAllowed(available);
      const now = new Date();
      if (active.current?.period.expires <= now) show(null);
      if (!available || offeredThisVisit || active.current) return;
      const period = pickHuiguanReminder(RecordsStore.loadCases(), now, getHuiguanReminded());
      if (period) show(period);
    };
    const queue = () => {
      if (!queued) queued = setTimeout(() => { queued = 0; check(); }, 140);
    };
    const foreground = () => {
      if (document.visibilityState !== 'hidden' && !active.current) offeredThisVisit = false;
      check();
    };
    const observer = new MutationObserver(queue);
    observer.observe(document.body, {
      subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden', 'open'],
    });
    document.addEventListener('visibilitychange', foreground);
    document.addEventListener('focusin', queue);
    document.addEventListener('focusout', queue);
    document.addEventListener('input', queue);
    window.addEventListener('focus', foreground);
    const interval = setInterval(queue, 60_000);
    check();
    return () => {
      clearTimeout(queued);
      clearInterval(interval);
      observer.disconnect();
      document.removeEventListener('visibilitychange', foreground);
      document.removeEventListener('focusin', queue);
      document.removeEventListener('focusout', queue);
      document.removeEventListener('input', queue);
      window.removeEventListener('focus', foreground);
    };
  }, []);

  useEffect(() => {
    if (!flow || !allowed) return;
    let frame = 0;
    let last = null;
    const tick = now => {
      if (active.current !== flow) return;
      if (!mayShow()) {
        setAllowed(false);
        return;
      }
      if (flow.period.expires <= new Date()) {
        show(null);
        return;
      }
      if (last === null && !flow.guide) {
        offeredThisVisit = true;
        markHuiguanReminded(flow.period);
      }
      if (last !== null) flow.remaining = Math.max(0, flow.remaining - (now - last));
      last = now;
      if (progress.current) progress.current.style.transform = `scaleX(${flow.remaining / HUIGUAN_CARD_MS})`;
      if (flow.remaining <= 0) show(flow.guide ? null : flow.period, !flow.guide);
      else frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [flow, allowed]);

  if (!flow || !allowed) return null;
  const { period, guide } = flow;
  const cycle = period.range === 'month' ? '月' : '周';
  const acted = () => {
    offeredThisVisit = true;
    markHuiguanReminded(period);
  };
  return createPortal(<>
    <style>{reminderCss}</style>
    <aside key={guide ? 'guide' : period.key + period.range} className={'hg-toast' + (guide ? ' hg-toast-guide' : '')}
      data-hg-reminder={period.range} data-hg-phase={guide ? 'guide' : 'offer'} aria-label="回观提醒">
      <svg aria-hidden="true" className="hg-toast-map" viewBox="0 0 520 70" preserveAspectRatio="xMidYMid slice" fill="none">
        <g stroke="currentColor">
          <path d="M247 78C324 7 412-12 532 1M323 81C380 31 463 20 528 26" strokeWidth=".65"/>
          <path d="M325 74C386 42 427 6 520 15" strokeWidth=".5" strokeDasharray="1 4" opacity=".64"/>
          <path d="M338 46l32-13 29 4 29-21 31 3 38-11" strokeWidth=".66" opacity=".82"/>
        </g>
        <g fill="currentColor">
          <circle cx="338" cy="46" r="1.35"/><circle cx="370" cy="33" r="1.6"/><circle cx="399" cy="37" r="1.15"/>
          <circle cx="428" cy="16" r="1.8"/><circle cx="459" cy="19" r="1.15"/><circle cx="497" cy="8" r="1.45"/>
          <circle cx="317" cy="25" r=".8"/><circle cx="472" cy="40" r=".8"/>
        </g>
      </svg>
      <div className="hg-toast-inner">
        <div className="hg-toast-copy">
          {!guide && <div className="hg-toast-eyebrow">遂问 · {cycle}回观</div>}
          <div role="status" className="hg-toast-message">
            {guide ? '想手动回观？' : `本${cycle}回观统计已生成，是否查看？`}
            {guide && <div className="hg-toast-route">设置 <i>›</i> 关于 <i>›</i> <b>开始回观</b></div>}
          </div>
        </div>
        <div className="hg-toast-actions">
          {guide ? <button type="button" onClick={() => show(null)}>跳过</button> : <>
            <button type="button" onClick={() => { acted(); show(period, true); }}>暂不查看</button>
            <button type="button" className="hg-toast-view" onClick={() => {
              acted(); show(null); onOpen?.(document.getElementById('blank'), period);
            }}>查看回观 <span aria-hidden="true">→</span></button>
          </>}
        </div>
      </div>
      {!guide && <div className="hg-toast-track" aria-hidden="true"><i ref={progress} /></div>}
    </aside>
  </>, document.body);
}
