import { useEffect, useRef, useState } from 'react';
import { RecordsStore } from '../data/recordsStore.js';
import { priorHuiguanPeriod } from '../data/huiguanStats.js';
import { getHuiguanReminded, markHuiguanReminded, pickHuiguanReminder } from '../data/huiguanReminder.js';
import reminderCss from '../styles/huiguanReminder.css?inline';

const busyLayer = '#xlrSplash:not(.out), #onbLayer.on, #setScreen.on, #sdb.on, #mask.on, .skinmenu, dialog[open]';
function mayShow() {
  if (document.visibilityState === 'hidden') return false;
  const active = document.activeElement;
  if (active?.matches('textarea, input:not([type="button"]), [contenteditable="true"]') &&
      String(active.value ?? active.textContent ?? '').trim()) return false;
  return ![...document.querySelectorAll(busyLayer)].some((el) => {
    const style = getComputedStyle(el);
    return style.display !== 'none' && style.visibility !== 'hidden';
  });
}

// One reminder per idle visit; a later foreground return can offer the other
// range if both were due. Starting another cast cannot replace the first.
let shownWindow = null;

const windowKey = now => priorHuiguanPeriod(now, 'month').key + ':' + priorHuiguanPeriod(now, 'week').key;

export default function HuiguanReminder({ onOpen }) {
  const banner = useRef(null);
  const card = useRef(null);
  const [visible, setVisible] = useState(false);
  const [period, setPeriod] = useState(null);
  useEffect(() => {
    let timer = 0;
    const check = () => {
      const now = new Date();
      const active = mayShow();
      // Once placed, don't shift the blank pane under existing controls;
      // opening or dismissing the reminder consumes the card immediately.
      if (active) setVisible(true);
      if (card.current && priorHuiguanPeriod(now, card.current.range).key !== card.current.key) {
        card.current = null;
        setPeriod(null);
        shownWindow = null;
      }
      if (!active || card.current || shownWindow === windowKey(now)) return;
      const candidate = pickHuiguanReminder(RecordsStore.loadCases(), now, getHuiguanReminded());
      if (candidate) {
        card.current = candidate;
        setPeriod(candidate);
      }
    };
    const queue = () => {
      if (!timer) timer = setTimeout(() => { timer = 0; check(); }, 140);
    };
    const foreground = () => {
      // Returning to the app can offer the other period after dismissing the
      // first, never while a previously presented banner is still pending.
      if (!card.current) shownWindow = null;
      queue();
    };
    const hide = () => {
      if (document.visibilityState === 'visible') foreground();
      else if (timer) { clearTimeout(timer); timer = 0; }
    };
    const observer = new MutationObserver(queue);
    observer.observe(document.body, { subtree: true, childList: true, attributes: true,
      attributeFilter: ['class', 'hidden', 'open'] });
    document.addEventListener('visibilitychange', hide);
    document.addEventListener('focusin', queue);
    document.addEventListener('focusout', queue);
    document.addEventListener('input', queue);
    window.addEventListener('focus', foreground);
    const interval = setInterval(queue, 60_000);
    queue();
    return () => {
      clearTimeout(timer); clearInterval(interval); observer.disconnect();
      document.removeEventListener('visibilitychange', hide);
      document.removeEventListener('focusin', queue);
      document.removeEventListener('focusout', queue);
      document.removeEventListener('input', queue);
      window.removeEventListener('focus', foreground);
    };
  }, []);
  useEffect(() => {
    const el = banner.current;
    if (!el || !period || !visible) return;
    const displayed = () => {
      if (!mayShow() || !el.isConnected || card.current !== period) return;
      shownWindow = windowKey(new Date());
      markHuiguanReminded(period);
    };
    if (typeof IntersectionObserver === 'function') {
      const observation = new IntersectionObserver(entries => {
        if (entries.some(x => x.isIntersecting && x.intersectionRatio >= .4)) displayed();
      }, { threshold: .4 });
      observation.observe(el);
      return () => observation.disconnect();
    }
    const inspectBounds = () => {
      const r = el.getBoundingClientRect();
      if (r.height > 0 && r.top + r.height * .4 < window.innerHeight && r.bottom - r.height * .4 > 0) displayed();
    };
    inspectBounds();
    window.addEventListener('scroll', inspectBounds, true);
    window.addEventListener('resize', inspectBounds);
    return () => {
      window.removeEventListener('scroll', inspectBounds, true);
      window.removeEventListener('resize', inspectBounds);
    };
  }, [period, visible]);
  if (!period || !visible) return null;
  const title = period.range === 'month' ? '上月回观' : '上周回观';
  const dismiss = () => {
    shownWindow = windowKey(new Date());
    markHuiguanReminded(period);
    card.current = null;
    setPeriod(null);
  };
  return <>
    <style>{reminderCss}</style>
    <aside className="hg-reminder" data-hg-reminder={period.range} aria-label={title} ref={banner}>
      <button type="button" className="hg-reminder-open" onClick={e => {
        const fallbackFocus = e.currentTarget.closest('#blank');
        dismiss();
        onOpen?.(fallbackFocus, period);
      }}><strong>{title}</strong><span>{period.total} 则卦例</span><span aria-hidden="true" className="hg-reminder-arrow">›</span></button>
      <button type="button" className="hg-reminder-close" onClick={dismiss} aria-label="忽略回观提醒">×</button>
    </aside>
  </>;
}
