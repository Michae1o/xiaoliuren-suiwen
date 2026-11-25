/* FogControls.jsx —— 设置页「背景雾气」的两件控件（液态玻璃 + 跟手 + 非线性收尾）
 *
 * 移植自安卓 SettingsText.kt：
 *   · FogLevelControl()：三档（静态雾景 / 轻柔流动 / 实时流雾），横拖跟手、松手就近吸附，
 *     头尾用各自的阻尼弹簧，连点可接续当前形状。
 *   · GlassSwitch()：44×26 轨道、20 旋钮、可走 18；拖多少给多少，松手过半数即开；
 *     轨道主色浓度 0.58×p。
 *
 * 分段动效用 RAF，并显式遵守减少动态效果设置。冻结开关保持原 WAAPI。
 */
import { useEffect, useLayoutEffect, useRef } from 'react';
import { FogCore } from '../data/fogCore.js';

const EASE = 'cubic-bezier(.22,1,.36,1)';   /* 安卓 KY_EASE_OUT */

function cancelAnim(el) {
  if (!el) return;
  try { if (el._fa) el._fa.cancel(); } catch (e) { }
  el._fa = null;
}

/* 三档分段控件。头尾各自阻尼，切档时保留当前两端的位置与速度。 */
const clampFog = (value, min, max) => Math.max(min, Math.min(max, value));

export function FogLevelControl({ level, onLevel }) {
  const trackRef = useRef(null);
  const thumbRef = useRef(null);
  const motion = useRef({
    left: level, right: level + 1, leftV: 0, rightV: 0,
    target: level, direction: 1, press: 0, pressTarget: 0,
    width: 0, frame: 0, last: 0, reduced: false, gesture: null, ignoreClickUntil: 0,
  });
  const s = motion.current;

  const paint = () => {
    const thumb = thumbRef.current;
    if (!thumb || !s.width) return;
    const cell = s.width / 3;
    const span = clampFog(s.right - s.left, 0.76, 1.42);
    const center = clampFog((s.left + s.right) * 0.5, span * 0.5, 3 - span * 0.5);
    const height = 1 - s.press - Math.min(0.09, Math.abs(span - 1) * 0.22);
    thumb.style.width = Math.max(1, span * cell - 6) + 'px';
    thumb.style.transform = 'translate3d(' + ((center - span * 0.5) * cell + 3) +
      'px,0,0) scaleY(' + height + ')';
  };

  const settle = () => {
    if (s.frame) cancelAnimationFrame(s.frame);
    s.frame = 0;
    s.last = 0;
    s.left = s.target;
    s.right = s.target + 1;
    s.leftV = s.rightV = 0;
    s.press = 0;
    paint();
  };

  const frame = (now) => {
    s.frame = 0;
    if (s.reduced) { settle(); return; }
    const dt = s.last ? clampFog((now - s.last) / 1000, 0, 0.04) : 1 / 60;
    s.last = now;
    // Substeps keep rebounds stable if the WebView drops one or two frames.
    const steps = Math.max(1, Math.ceil(dt * 120)), h = dt / steps;
    const dragging = s.gesture?.dragging;
    const frontK = dragging ? 815 : 485, frontD = dragging ? 45 : 28;
    const backK = dragging ? 530 : 278, backD = dragging ? 36 : 21;
    const kL = s.direction < 0 ? frontK : backK, dL = s.direction < 0 ? frontD : backD;
    const kR = s.direction > 0 ? frontK : backK, dR = s.direction > 0 ? frontD : backD;
    for (let i = 0; i < steps; i++) {
      s.leftV += (kL * (s.target - s.left) - dL * s.leftV) * h;
      s.rightV += (kR * (s.target + 1 - s.right) - dR * s.rightV) * h;
      s.left += s.leftV * h;
      s.right += s.rightV * h;
    }
    s.press += (s.pressTarget - s.press) * (1 - Math.exp(-dt * 27));
    if (Math.abs(s.target - s.left) < 0.0015 && Math.abs(s.target + 1 - s.right) < 0.0015 &&
        Math.abs(s.leftV) + Math.abs(s.rightV) < 0.022 && Math.abs(s.press - s.pressTarget) < 0.001) {
      s.left = s.target;
      s.right = s.target + 1;
      s.leftV = s.rightV = 0;
      s.press = s.pressTarget;
      s.last = 0;
      paint();
      return;
    }
    paint();
    s.frame = requestAnimationFrame(frame);
  };
  const wake = () => {
    if (s.reduced) settle();
    else if (!s.frame) s.frame = requestAnimationFrame(frame);
  };
  const aim = (next) => {
    if (next !== s.target) s.direction = Math.sign(next - s.target);
    s.target = next;
    wake();
  };
  const measure = () => {
    s.width = trackRef.current?.clientWidth || 0;
    paint();
  };

  useLayoutEffect(() => {
    const track = trackRef.current;
    // Respect the Windows accessibility preference even though the physics
    // uses RAF (legacy CSS disables only CSS transitions in reduced mode).
    const prefers = window.matchMedia('(prefers-reduced-motion: reduce)');
    const syncPreference = () => {
      s.reduced = prefers.matches;
      if (s.reduced) settle();
    };
    syncPreference();
    measure();
    const observer = typeof ResizeObserver === 'function' ? new ResizeObserver(measure) : null;
    if (track) observer?.observe(track);
    prefers.addEventListener?.('change', syncPreference);
    window.addEventListener('resize', measure);
    return () => {
      observer?.disconnect();
      prefers.removeEventListener?.('change', syncPreference);
      window.removeEventListener('resize', measure);
      if (s.frame) cancelAnimationFrame(s.frame);
      s.frame = s.last = 0;
    };
  }, []);
  useLayoutEffect(() => {
    if (!s.gesture) aim(level);
  }, [level]);

  const pointerCell = (e) => {
    const bounds = trackRef.current.getBoundingClientRect();
    return 3 * (e.clientX - bounds.left) / Math.max(1, bounds.width);
  };
  const down = (e) => {
    if (e.button !== 0 || e.isPrimary === false || s.gesture) return;
    const cell = pointerCell(e);
    const center = (s.left + s.right) * 0.5;
    s.ignoreClickUntil = 0;
    s.gesture = {
      id: e.pointerId, x: e.clientX, dragging: false,
      offset: Math.abs(cell - center) <= 0.5 ? cell - center : 0,
    };
    s.pressTarget = 0.045;
    // Capture on the button (not the rail) so an ordinary tap still produces
    // its native click. Only a real drag suppresses that click.
    try { e.target.closest('button')?.setPointerCapture?.(e.pointerId); } catch { /* pointer already ended */ }
    wake();
  };
  const move = (e) => {
    const g = s.gesture;
    if (!g || g.id !== e.pointerId) return;
    if (!g.dragging && Math.abs(e.clientX - g.x) < 5) return;
    g.dragging = true;
    s.pressTarget = 0.075;
    aim(clampFog(pointerCell(e) - g.offset - 0.5, 0, 2));
  };
  const finish = (e, cancel = false) => {
    const g = s.gesture;
    if (!g || g.id !== e.pointerId) return;
    s.gesture = null;
    s.pressTarget = 0;
    if (g.dragging) s.ignoreClickUntil = performance.now() + 420;
    if (cancel || !g.dragging) { aim(level); return; }
    const next = Math.round(clampFog(pointerCell(e) - g.offset - 0.5, 0, 2));
    aim(next);
    if (next !== level) onLevel(next);
  };
  const select = (e, index) => {
    if (e.detail && performance.now() < s.ignoreClickUntil) {
      s.ignoreClickUntil = 0;
      return;
    }
    aim(index);
    if (index !== level) onLevel(index);
  };

  return (
    <div className="fg-seg fg-glass" id="stFogLevel" ref={trackRef}
      role="group" aria-label="背景雾气"
      onPointerDown={down} onPointerMove={move} onPointerUp={finish}
      onPointerCancel={(e) => finish(e, true)} onLostPointerCapture={(e) => finish(e, true)}>
      <span className="fg-thumb" ref={thumbRef} aria-hidden="true"></span>
      <div className="fg-cells">
        {FogCore.FOG_LEVELS.map((t, i) => (
          <button key={t} type="button" aria-current={i === level ? 'true' : 'false'}
            onClick={(e) => select(e, i)}>{t}</button>
        ))}
      </div>
    </div>
  );
}

/* 冻结开关 */
export function FogSwitch({ checked, onChange, label, hint }) {
  const wrapRef = useRef(null);
  const knobRef = useRef(null);
  const onRef = useRef(null);
  const st = useRef({ p: checked ? 1 : 0, dragging: false });
  const lastUp = useRef(0);

  const paintKnob = () => {
    const s = st.current;
    if (knobRef.current) {
      knobRef.current.style.transform = 'translate3d(' + (18 * s.p) + 'px,0,0) scale(' +
        (1 + (s.dragging ? 0.06 : 0)) + ')';
    }
    if (onRef.current) onRef.current.style.opacity = String(0.58 * s.p);
  };

  const tween = (to) => {
    const s = st.current;
    const knob = knobRef.current, on = onRef.current;
    const from = s.p;
    if (Math.abs(from - to) < 0.001) { paintKnob(); return; }
    s.p = to;
    if (knob) {
      cancelAnim(knob);
      const a = knob.animate(
        [{ transform: 'translate3d(' + (18 * from) + 'px,0,0)' },
         { transform: 'translate3d(' + (18 * to) + 'px,0,0)' }],
        { duration: 200, easing: EASE, fill: 'forwards' });
      knob._fa = a;
      a.onfinish = () => { cancelAnim(knob); paintKnob(); };
    }
    if (on) {
      cancelAnim(on);
      const b = on.animate([{ opacity: 0.58 * from }, { opacity: 0.58 * to }],
        { duration: 200, easing: EASE, fill: 'forwards' });
      on._fa = b;
      b.onfinish = () => { cancelAnim(on); paintKnob(); };
    }
  };

  useEffect(() => { tween(checked ? 1 : 0); }, [checked]);
  useEffect(() => { paintKnob(); }, []);

  const down = (e) => {
    st.current.dragging = true;
    st.current.moved = false;
    cancelAnim(knobRef.current);
    cancelAnim(onRef.current);
    try { wrapRef.current.setPointerCapture(e.pointerId); } catch (er) { }
    paintKnob();
  };
  const move = (e) => {
    if (!st.current.dragging) return;
    const rect = wrapRef.current.getBoundingClientRect();
    const dx = e.clientX - (rect.left + 3 + 10);
    st.current.p = Math.max(0, Math.min(1, 0.5 + dx / 18));
    st.current.moved = true;
    paintKnob();
  };
  const up = () => {
    if (!st.current.dragging) return;
    st.current.dragging = false;
    lastUp.current = Date.now();
    /* 没拖动 = 点击：直接翻转（安卓那边整行 toggleable，这里点到旋钮也应该管用） */
    if (!st.current.moved) {
      const nx = checked ? 0 : 1;
      tween(nx);
      onChange(!checked);
      return;
    }
    st.current.moved = false;
    const on = st.current.p >= 0.5;
    const next = on ? 1 : 0;
    tween(next);
    if (on !== checked) onChange(on);
  };

  /* 整行可点（安卓是 toggleable 的 Row）：真实的指针拖动由上面的跟手处理；
     键盘 / 脚本的 click 走这里。⚠ 指针松手后浏览器还会补一次 click ⇒ 300ms 内忽略，
     否则一次拖动会被自己反弹回去。 */
  const rowClick = (e) => {
    if (Date.now() - lastUp.current < 300) return;
    onChange(!checked);
  };

  return (
    <div className="fg-freeze" id="stFogFreezeRow" onClick={rowClick}>
      <div className="fg-lab">
        <div className="fg-t">{label}
          <em className={checked ? '' : 'off'} id="stFogFreezeState">{checked ? '已冻结' : '流动中'}</em>
        </div>
        <div className="fg-d" id="stFogFreezeHint">{hint}</div>
      </div>
      <div className="fg-sw" id="stFogFreeze" role="switch" aria-checked={checked}
        aria-label="冻结雾气" ref={wrapRef}
        onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
        onClick={(e) => {
          /* 脚本 / 键盘的 click：指针松手后浏览器补的那一次由 lastUp 挡掉 */
          if (Date.now() - lastUp.current < 300) { e.stopPropagation(); return; }
          e.stopPropagation();
          onChange(!checked);
        }}>
        <span className="fg-sw-track"></span>
        <span className="fg-sw-on" ref={onRef} style={{ opacity: checked ? 0.58 : 0 }}></span>
        <span className="fg-sw-knob" ref={knobRef}></span>
      </div>
    </div>
  );
}
