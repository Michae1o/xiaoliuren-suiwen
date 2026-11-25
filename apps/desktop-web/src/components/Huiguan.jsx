import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { motion, useReducedMotion, useSpring, useTransform } from 'motion/react';
import { summarizeHuiguan } from '../data/huiguanStats.js';
import { TRACE_LIMIT } from '../data/huiguanTrace.js';
import { getHuiguanSeason } from '../data/huiguanSeason.js';
import HuiguanInstrument from './HuiguanInstrument.jsx';
import { getHuiguanMaterial, getHuiguanMaterialRenderer } from '../data/huiguanMaterialTerm.js';
import HuiguanMaterials, { SpringPaperBackdrop, MaterialWeatherBackdrop } from './HuiguanMaterials.jsx';
import HuiguanSeasonStudies from './HuiguanSeasonStudies.jsx';
import SpringSeasonMaterials from './SpringSeasonMaterials.jsx';
import AutumnSeasonMaterials from './AutumnSeasonMaterials.jsx';
import SunSeasonMaterials from './SunSeasonMaterials.jsx';
import WinterSeasonMaterials from './WinterSeasonMaterials.jsx';
import HuiguanWaterReflection from './HuiguanWaterReflection.jsx';
import SpringBalance from './SpringBalance.jsx';

const renderers = {spring:SpringSeasonMaterials, autumn:AutumnSeasonMaterials,
  sun:SunSeasonMaterials, winter:WinterSeasonMaterials};

const pages = ['卦数', '事类', '实际情况'];
const two = (n) => String(n).padStart(2, '0');
const day = (date) => date.getFullYear() + '.' + two(date.getMonth() + 1) + '.' + two(date.getDate());

const reportEnter = { duration: 270, easing: 'cubic-bezier(.2,.72,.22,1)', fill: 'both' };
const reportExit = { duration: 190, easing: 'cubic-bezier(.45,0,.56,1)', fill: 'both' };
const shownFrame = { opacity: '1', transform: 'none' };
const hiddenFrame = { opacity: '0', transform: 'translateY(8px) scale(.996)' };
const motionPreference = () => window.matchMedia?.('(prefers-reduced-motion: reduce)');

function playReport(el, frames, options) {
  try { return el.animate?.(frames, options) ?? null; } catch { return null; }
}


function Arrow({ right }) {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={right ? 'M5 12h14m-6-6 6 6-6 6' : 'M19 12H5m6 6-6-6 6-6'} /></svg>;
}

function Scene({ i, active, progress, stats, season }) {
  const delta = useTransform(progress, (p) => Math.max(-1, Math.min(1, p - i)));
  // Two staggered cues instead of moving the entire page: text releases its
  // place, the underlying camera pans, then new typography resolves.
  const opacity = useTransform(delta, [-1, -.57, -.12, .10, .58, 1], [0, 0, 1, 1, 0, 0]);
  const x = useTransform(delta, [-1, 0, 1], ['2.6vw', '0vw', '-2.6vw']);
  const date = <p className="hg-date">{day(stats.start)} <span aria-hidden="true">—</span><span className="hg-sr">至</span> {day(stats.last)}</p>;
  return <motion.section className={'hg-page hg-page-' + i} aria-hidden={!active} aria-labelledby={'hg-page-' + i}
    style={{ opacity, x }}>
    {i === 0 && <div className="hg-overview">
      <h3 id="hg-page-0" className="hg-term">{season?.term || '本周'}</h3>
      <div className="hg-number"><span>{stats.total}</span><small>卦例</small></div>
      {date}
    </div>}
    {i === 1 && <>
      <h3 id="hg-page-1" className="hg-category-title">事类</h3>
      {stats.categories.length ?
        <ul className={'hg-categories' + (stats.categories.length > 3 ? ' hg-categories-dense' : '')}
          data-hg-length={stats.categories.length}
          tabIndex={active && stats.categories.length > 3 ? 0 : -1} aria-label="本期事类统计">
          {stats.categories.map((entry, i) => <li key={entry.id} data-hg-rank={i}>
            <b>{entry.count}</b><span className="hg-category-name">{entry.label}</span>
          </li>)}
        </ul> : <p className="hg-empty">暂无卦例</p>}
    </>}
    {i === 2 && <>
      <h3 id="hg-page-2" className="hg-status-title">实际情况</h3>
      <div className="hg-ledger">
        <div className="hg-ledger-noted"><b>{stats.noted}<small className="hg-sr">则</small></b><span>已记<span className="hg-sr">实际情况</span></span></div>
        <div className="hg-ledger-pending"><b>{stats.total - stats.noted}<small className="hg-sr">则</small></b><span>未记<span className="hg-sr">实际情况</span></span></div>
      </div>
      <p className="hg-sr">本页只统计是否填写实际情况，不判断是否应验。</p>
    </>}
  </motion.section>;
}

export default function Huiguan({ records, now, seasonDate = now, prior = null, onClose, trigger }) {
  const dialog = useRef(null);
  const close = useRef(null);
  const drag = useRef(null);
  const outer = useRef({ animation: null, timeout: 0, closing: false, finish: null });
  const [closing, setClosing] = useState(false);
  const [range, setRange] = useState(prior?.range ?? 'week');
  const [page, setPage] = useState(0);
  const progress = useSpring(0, { stiffness: 92, damping: 21, mass: .94 });
  const reduced = useReducedMotion();
  const [dragging, setDragging] = useState(false);
  const shade = useTransform(progress, [0,.16,1,1.16,2], [0,0,1,1,0]);
  const afterglow = useTransform(progress, [0,1.12,1.9,2], [0,0,1,1]);
  const stats = useMemo(() => summarizeHuiguan(records, prior?.anchor ?? now, range), [records, now, range, prior]);
  const seasonAnchor = prior?.anchor ?? seasonDate;
  const season = useMemo(() => range === 'month' ? getHuiguanSeason(seasonAnchor) : null, [seasonAnchor, range]);
  const material = getHuiguanMaterial(season);
  const studies = material === 'thunder' || material === 'ripples' || material === 'canopy' || material === 'grainfill';
  const SeasonArt = renderers[getHuiguanMaterialRenderer(material)];
  const seasonColors = season ? {
    '--hg-tone-ink': season.colors.ink, '--hg-tone-tint': season.colors.accent,
    '--hg-tone-air': season.colors.secondary, '--hg-tone-glow': season.colors.glow,
  } : undefined;
  const go = (n) => setPage(Math.max(0, Math.min(pages.length - 1, n)));
  const shift = (by) => setPage((n) => Math.max(0, Math.min(pages.length - 1, n + by)));
  useLayoutEffect(() => {
    if (reduced) progress.jump(page);
    else if (!dragging) progress.set(page);
  }, [progress, page, dragging, reduced]);

  useLayoutEffect(() => {
    const el = dialog.current;
    const state = outer.current;
    const media = motionPreference();
    if (!el.open) el.showModal();
    close.current?.focus({ preventScroll: true });
    if (!media?.matches) {
      const entry = playReport(el, [hiddenFrame, shownFrame], reportEnter);
      state.animation = entry;
      entry?.finished?.then(() => {
        if (state.animation !== entry || state.closing) return;
        state.animation = null;
        entry.cancel();
      }, () => {});
    }
    const reduce = (e) => {
      if (!e.matches) return;
      if (state.finish) state.finish();
      else { state.animation?.cancel(); state.animation = null; }
    };
    media?.addEventListener?.('change', reduce);
    return () => {
      media?.removeEventListener?.('change', reduce);
      clearTimeout(state.timeout);
      state.animation?.cancel();
      state.animation = null;
      state.finish = null;
      state.timeout = 0;
      state.closing = false;
      if (el.open) el.close();
      if (trigger?.isConnected && !trigger.closest('#setScreen:not(.on), #sdb:not(.on)')) trigger.focus({ preventScroll: true });
    };
  }, [trigger]);

  const requestClose = () => {
    const el = dialog.current;
    const state = outer.current;
    if (state.closing) return;
    state.closing = true;
    drag.current = null;
    if (!el?.open || reduced || motionPreference()?.matches) {
      onClose();
      return;
    }
    // A fast close during entry reverses from the rendered frame, not from opacity 1.
    const current = state.animation ? getComputedStyle(el) : shownFrame;
    const from = { opacity: current.opacity ?? '1', transform: current.transform || 'none' };
    state.animation?.cancel();
    const exit = playReport(el, [from, hiddenFrame], reportExit);
    state.animation = exit;
    if (!exit) { onClose(); return; }
    setClosing(true);
    const finish = () => {
      if (state.finish !== finish) return;
      state.finish = null;
      clearTimeout(state.timeout);
      state.timeout = 0;
      onClose();
    };
    state.finish = finish;
    exit.finished?.then(finish, () => {});
    // A detached/paused animation must never strand a full-window modal.
    state.timeout = setTimeout(finish, reportExit.duration + 110);
  };

  const finish = (e, cancel = false) => {
    const g = drag.current;
    if (!g || g.id !== e.pointerId) return;
    drag.current = null;
    if (g.axis === 'x' && !cancel) {
      const dx = e.clientX - g.x;
      const width = e.currentTarget.clientWidth;
      const elapsed = Math.max(1, performance.now() - g.time);
      if (Math.abs(dx) >= Math.min(width * 0.2, 120) || (Math.abs(dx) > 36 && Math.abs(dx) / elapsed > 0.42)) {
        shift(dx < 0 ? 1 : -1);
      }
    }
    setDragging(false);
  };

  return <dialog ref={dialog} className="hg-dialog" data-hg-mode={season?.mode || "week"}
    data-hg-material-term={material === 'equinox-dusk' ? material : undefined} style={seasonColors} aria-labelledby="hg-title" onCancel={(e) => { e.preventDefault(); requestClose(); }}
    onKeyDown={(e) => {
      // Keep Escape in the report, including for old window-level shell handlers.
      if (e.key === 'Escape' && !e.isComposing) {
        e.preventDefault(); e.stopPropagation(); requestClose(); return;
      }
      if (outer.current.closing) { e.preventDefault(); e.stopPropagation(); return; }
      if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey || e.isComposing ||
        (e.target.closest?.('.hg-categories') && e.target.scrollWidth > e.target.clientWidth)) return;
      if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
        e.preventDefault(); e.stopPropagation(); shift(e.key === 'ArrowLeft' ? -1 : 1);
      }
    }}>
    <div className="hg-layout" data-hg-page={page} inert={closing}>
      <motion.div className="hg-paper-after" style={{ opacity: afterglow }} aria-hidden="true" />
      <motion.div className="hg-paper-shade" style={{ opacity: shade }} aria-hidden="true" />
      <div className="hg-paper-grain" aria-hidden="true" />
      {material === 'spring' && <SpringPaperBackdrop timeline={progress} reduced={!!reduced}/>}
      {material === 'frost' &&
        <MaterialWeatherBackdrop key={material} type={material} timeline={progress} reduced={!!reduced} paused={closing}/>}
      {studies && <HuiguanSeasonStudies key={material} type={material} timeline={progress} reduced={!!reduced}/>}
      {SeasonArt && <SeasonArt key={material} name={material} timeline={progress} reduced={!!reduced}/>}
      <header className="hg-header">
        <div className="hg-title-block">
          <button type="button" className="hg-close" data-huiguan-close aria-label="返回" title="返回" ref={close} onClick={requestClose}>
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M18.5 12h-13m0 0 5-5m-5 5 5 5" /></svg>
          </button>
          <h2 id="hg-title">回观</h2>
          <details className="hg-sources"><summary aria-label="星图资料">ⓘ</summary>
            <div><b>二十八宿主星官选图</b>
              <p>据宋代星官复原绘制，星位回推至约 1052 年。选绘二十八宿主星官、三垣藩墙、北极星官和北斗，不是实时天象。</p>
              <p><a href="https://github.com/Stellarium/stellarium/tree/v26.2/skycultures/chinese_song_dynasty" target="_blank" rel="noreferrer">孙殳玮 · Stellarium 宋代星空</a><br />
                <a href="https://github.com/astronexus/HYG-Database/blob/main/hyg/README.md" target="_blank" rel="noreferrer">David Nash · HYG v4.1</a><br />
                <a href="https://creativecommons.org/licenses/by-sa/4.0/" target="_blank" rel="noreferrer">改编数据及星图 · CC BY-SA 4.0</a></p>
              {(stats.total > TRACE_LIMIT || stats.categories.length > 9) && <p>数字是全量。{stats.total > TRACE_LIMIT ? `卦点抽样至 ${TRACE_LIMIT} 枚。` : ''}{stats.categories.length > 9 ? '低频事类只在点图中合并。' : ''}</p>}
            </div>
          </details>
        </div>
        {prior ? <span className="hg-period-label">{(now > prior.anchor && day(now) !== day(prior.anchor) ? '上' : '本') + (range === 'month' ? '月' : '周')}</span> :
          <div className="hg-range" role="group" aria-label="统计时段">
            <button type="button" aria-pressed={range === 'week'} onClick={() => setRange('week')}>本周</button>
            <button type="button" aria-pressed={range === 'month'} onClick={() => setRange('month')}>本月</button>
          </div>}
      </header>
      <div className={'hg-viewport' + (dragging ? ' is-dragging' : '')} onPointerDown={(e) => {
        if (!e.isPrimary || e.button !== 0) return;
        const categories = e.target.closest?.('.hg-categories');
        if (categories && (categories.scrollWidth > categories.clientWidth || categories.scrollHeight > categories.clientHeight)) return;
        drag.current = { id: e.pointerId, x: e.clientX, y: e.clientY, time: performance.now(), axis: '' };
      }} onPointerMove={(e) => {
        const g = drag.current;
        if (!g || g.id !== e.pointerId) return;
        const dx = e.clientX - g.x, dy = e.clientY - g.y;
        if (!g.axis && Math.max(Math.abs(dx), Math.abs(dy)) > 9) {
          g.axis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y';
          if (g.axis === 'x') {
            // Scrub from the currently painted camera position, including
            // when a second gesture interrupts the settling spring.
            g.origin = progress.get();
            e.currentTarget.setPointerCapture(e.pointerId);
            setDragging(true);
          }
        }
        if (g.axis !== 'x') return;
        const w = Math.max(e.currentTarget.clientWidth, 1);
        const shown = Math.max(0, Math.min(2, g.origin - dx / (w * .73)));
        if (!reduced) progress.jump(shown);
      }} onPointerUp={(e) => finish(e)} onPointerCancel={(e) => finish(e, true)}
      onLostPointerCapture={(e) => finish(e, true)} onPointerLeave={(e) => {
        if (!e.currentTarget.hasPointerCapture(e.pointerId)) finish(e, true);
      }}>
        <HuiguanInstrument stats={stats} timeline={progress} season={season} dewMist={material === 'dew'}
          rain={material === 'rain'} rainReduced={!!reduced} rainPaused={closing || dragging}
          materialOverlay={(material === 'grain' || material === 'snow') ?
            <HuiguanMaterials key={material} type={material} timeline={progress} reduced={!!reduced}/> :
            material === 'swallows' ? <SpringBalance timeline={progress} reduced={!!reduced}/> : null} />
        {material === 'blossoms' && <HuiguanWaterReflection timeline={progress} reduced={!!reduced} paused={closing}/>}
        <div className="hg-track">
          {pages.map((title, i) => <Scene key={title} i={i} active={page === i} progress={progress}
            stats={stats} season={season} />)}
        </div>
      </div>
      <footer className="hg-footer">
        <button type="button" className="hg-direction" aria-disabled={page === 0}
          onClick={() => shift(-1)} aria-label="上一页"><Arrow /> <span>上一页</span></button>
        <nav className="hg-pager" aria-label="回观页码">
          {pages.map((title, i) => <button type="button" key={title} aria-label={'第' + (i + 1) + '页：' + title}
            aria-current={page === i ? 'page' : undefined} onClick={() => go(i)}><span aria-hidden="true" /></button>)}
        </nav>
        <button type="button" className="hg-direction" aria-disabled={page === pages.length - 1}
          onClick={() => shift(1)} aria-label="下一页"><span>下一页</span> <Arrow right /></button>
      </footer>
      {stats.total > TRACE_LIMIT && <p className="hg-sr">星图抽样显示 {TRACE_LIMIT} 个卦例点，数字为全量。</p>}
      <p className="hg-sr" role="status">{'第 ' + (page + 1) + ' / 3 页，' + pages[page]}</p>
    </div>
  </dialog>;
}
