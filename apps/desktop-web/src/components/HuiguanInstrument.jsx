import { useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { motion, useTransform } from 'motion/react';
import { buildHuiguanTrace } from '../data/huiguanTrace.js';
import atlas from '../data/huiguan-atlas/song-1052.json';
import HuiguanRainOptics from './HuiguanRainOptics.jsx';
import HuiguanDewMist from './HuiguanDewMist.jsx';
import { AtmosphereEdgeMask, atmosphereEdges } from './HuiguanAtmosphereEdges.jsx';
import skyStyle from '../styles/huiguan-sky.css?inline';

const W = 1344, H = 758, ATLAS_RADIUS = 392, TAU = Math.PI * 2;
const bound = n => Math.max(0, Math.min(1, n));
const ease = n => { const t = bound(n); return t * t * (3 - 2 * t); };
const mix = (a, b, t) => a + (b - a) * t;
const polar = (cx, cy, radius, angle) => [cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)];
const short = n => Number(n.toFixed(2));
const svgLine = (points) => points.map(([x, y], i) => `${i ? 'L' : 'M'}${short(x)} ${short(y)}`).join('');
const traceDaysAngle = (index, count) => -Math.PI / 2 + index / count * TAU;
const boundary = q => (q.boundaryRa - atlas.projection.thetaRaOffset) * Math.PI / 180;

const shot = [
  { x: 927 / W, y: 323 / H },
  { x: 1106 / W, y: 171 / H },
  { x: 127 / W, y: 509 / H },
];
const smallShot = [
  { x: .548, y: .370 },
  { x: .836, y: .226 },
  { x: .063, y: .612 },
];
function atlasPose(p, width, compact) {
  const frames = compact ? smallShot : shot;
  const t = p < 1 ? ease(p) : ease(p - 1);
  const i = p < 1 ? 0 : 1;
  const radius = Math.min(ATLAS_RADIUS, width * (compact ? .57 : .35));
  return {
    x: mix(frames[i].x, frames[i + 1].x, t) * width,
    y: mix(frames[i].y, frames[i + 1].y, t) * H,
    radius,
  };
}
const constantStars = atlas.stars;
const starXY = (id) => {
  const { x, y } = constantStars[id];
  return [x * ATLAS_RADIUS, y * ATLAS_RADIUS];
};
const starLines = (asterism) => asterism.lines.map(line => svgLine(line.map(starXY)));
const starSet = (asterism) => [...new Set(asterism.lines.flat())];

function DipperOptic({ radius, glow, index }) {
  const radii = [1.11, .86, .95, .67, 1.21, .97, 1.07];
  const b = radius * radii[index];
  const reach = b * (index === 3 ? 3.1 : 4.45);
  return <>
    <g className="hg-dipper-optics" style={{
      animationDuration: `${5.8 + (index % 4) * .77}s`,
      animationDelay: `${-(.63 + index * 1.31)}s`,
    }}>
      <ellipse rx={b * 7.2} ry={b * 6.1} fill={`url(#${glow.aura})`} transform="rotate(-21)" />
      <circle r={b * 3.43} fill={`url(#${glow.bloom})`} />
      <g opacity={index === 3 ? .42 : .78}>
        <ellipse rx={reach} ry={Math.max(.47, b * .33)} fill={`url(#${glow.ray})`} />
        <ellipse rx={reach * .85} ry={Math.max(.45, b * .31)} fill={`url(#${glow.ray})`} transform="rotate(90)" />
      </g>
    </g>
    <circle className="hg-dipper-kernel" r={radius * .88} />
    <circle className="hg-dipper-white-point" r={radius * .46} />
  </>;
}

// Local light is an interpretation of the ink, never a second star catalogue.
const wakeRanks = new Set([0, 2, 4, 6, 7, 9, 11, 12, 14, 17, 18, 20, 21, 23, 25, 27]);
const lineRanks = new Set([2, 6, 7, 12, 14, 18, 21, 27]);

function MansionWake({ x, y, rank, radius, glow }) {
  const azimuth = rank * 2.399963;
  const drift = 4.6 + (rank % 4) * .8;
  return <g transform={`translate(${x} ${y})`} className="hg-mansion-wake-anchor">
    <g className="hg-mansion-wake" style={{
      '--hg-wake-x': `${short(Math.cos(azimuth) * drift)}px`,
      '--hg-wake-y': `${short(Math.sin(azimuth) * drift)}px`,
      '--hg-wake-time': `${5.1 + (rank % 6) * .79}s`,
      '--hg-wake-delay': `${-((rank * 1.37 + 2.9) % 7.9)}s`,
    }}>
      <ellipse rx={radius * 4.3 + 2.7} ry={radius * 2.8 + 1.7}
        transform={`rotate(${short(azimuth * 180 / Math.PI)})`}
        fill={`url(#${glow.wake})`} />
    </g>
  </g>;
}

function Asterism({ asterism, glow, type, rank = -1 }) {
  const prominent = type === 'dipper';
  const wake = wakeRanks.has(rank);
  const flow = lineRanks.has(rank);
  const paths = starLines(asterism);
  return <g className={'hg-atlas-asterism ' + type} data-asterism={asterism.name}>
    {paths.map((d, i) => <path key={i} className="hg-atlas-line" d={d} />)}
    {flow && <path className="hg-mansion-current" d={paths[0]} pathLength={100} style={{
      '--hg-current-time': `${9.4 + (rank % 5) * 1.12}s`,
      '--hg-current-delay': `${-((rank * 1.91 + 6.2) % 10.7)}s`,
    }} />}
    {starSet(asterism).map((hip, index) => {
      const [cx, cy] = starXY(hip);
      const mag = constantStars[hip].mag;
      const radius = Math.min(2.6, Math.max(.85, 2.5 - mag * .31)) +
        (prominent ? 1.05 : Number(hip === asterism.anchorHip) * .36);
      return <g key={hip} data-hip={hip}>
        {prominent ? <g transform={`translate(${cx} ${cy})`} className="hg-dipper-star">
          <DipperOptic radius={radius} index={index} glow={glow} />
        </g> : <>
          {wake && hip === asterism.anchorHip &&
            <MansionWake x={cx} y={cy} rank={rank} radius={radius} glow={glow} />}
          <circle cx={cx} cy={cy} r={radius} className="hg-atlas-dot" />
        </>}
      </g>;
    })}
  </g>;
}

function Atlas({ timeline, width, compact, glow, labels = true, onlyLabels = false }) {
  const x = useTransform(timeline, p => atlasPose(p, width, compact).x);
  const y = useTransform(timeline, p => atlasPose(p, width, compact).y);
  const scale = atlasPose(0, width, compact).radius / ATLAS_RADIUS;
  const opacity = useTransform(timeline, [0, 1, 2], [.82, .52, .64]);
  return <motion.g className={onlyLabels ? 'hg-atlas-label-camera' : 'hg-atlas-camera'} data-atlas-epoch={onlyLabels ? undefined : atlas.projection.epoch}
      style={{ x, y, scale, opacity, originX: '0px', originY: '0px', color: onlyLabels ? 'var(--hg-atlas-current)' : undefined }}>
    {!onlyLabels && [.26, .50, 90 / 113, 1.17, 1.195].map((fraction, i) =>
      <circle key={fraction} r={ATLAS_RADIUS * fraction} className="hg-atlas-ring"
        opacity={[.42, .38, .62, .75, .42][i]} strokeDasharray={i === 2 ? '2.8 5.8' : undefined} />)}
    <g className="hg-atlas-boundaries">
      {atlas.mansions.map(q => {
        const t = boundary(q);
        // Keep exact boundary positions, including the very narrow 觜 sector.
        // Only the printed 觜 label is offset further out than its neighbours.
        const labelScale = q.name === '觜宿' ? 1.042 : .963;
        const x = q.sectorLabel.x * ATLAS_RADIUS * labelScale;
        const y = q.sectorLabel.y * ATLAS_RADIUS * labelScale;
        return <g key={q.name} data-mansion={onlyLabels ? undefined : q.name[0]} data-start-ra={onlyLabels ? undefined : q.boundaryRa}>
          {!onlyLabels && <path d={svgLine([polar(0, 0, ATLAS_RADIUS * 1.076, t), polar(0, 0, ATLAS_RADIUS * 1.196, t)])} />}
          {labels && <text x={x} y={y}>{q.name[0]}</text>}
        </g>;
      })}
    </g>
    {!onlyLabels && atlas.enclosures.filter(g => g.name !== '北斗').map(g =>
      <Asterism key={g.name} asterism={g} glow={glow} type="enclosure" />)}
    {!onlyLabels && atlas.mansions.map((q, rank) => <Asterism key={q.name} asterism={q} glow={glow} type="principal" rank={rank} />)}
    {!onlyLabels && atlas.enclosures.filter(g => g.name === '北斗').map(g =>
      <Asterism key={g.name} asterism={g} glow={glow} type="dipper" />)}
    {!onlyLabels && <g className="hg-atlas-north-pole">
      <circle r="4.4" />
      <path d="M-2 0h4m-2-2v4" />
    </g>}
  </motion.g>;
}

function DateScale({ timeline, width, compact, days }) {
  const opacity = useTransform(timeline, [0, .13, .58, 2], [.86, .78, 0, 0]);
  const pose = atlasPose(0, width, compact);
  return <motion.g className="hg-trace-date" style={{ opacity }}>
    {Array.from({ length: days }, (_, day) => {
      const isMajor = days <= 7 || day === 0 || day === days - 1 || (day + 1) % 5 === 0 && day + 2 < days;
      if (!isMajor) return null;
      const [x, y] = polar(pose.x, pose.y, pose.radius * .749, traceDaysAngle(day, days));
      return <text key={day} x={x} y={y}>{day + 1}</text>;
    })}
  </motion.g>;
}

const categoryCoordinates = [
  [637 / W, 230 / H], [1013 / W, 386 / H], [267 / W, 438 / H],
];
const categoryCoordinatesSmall = [
  [.51, .32], [.73, .47], [.29, .54],
];
function catPose(point, width, compact, groups) {
  let x, y, pack;
  if (groups <= 3) {
    [x, y] = (compact ? categoryCoordinatesSmall : categoryCoordinates)[point.category];
    pack = Math.min(compact ? 17 : 27, width * (compact ? .105 : .165) / Math.sqrt(Math.max(1, point.categoryCount)));
  } else {
    // Full counts stay in HTML. For longer catalogs we keep the sampled
    // marks in narrow bands rather than crowding the three summary landmarks.
    const cols = 3;
    const i = point.category % cols, j = Math.floor(point.category / cols);
    x = [.24, .51, .77][i] + (j % 2 ? .036 : 0);
    y = (compact ? .285 : .302) + j * (compact ? .118 : .134) + [.039, -.041, .062][i];
    pack = Math.min(compact ? 13 : 17, width * .087 / Math.sqrt(Math.max(1, point.categoryCount)));
  }
  return spiralPoint(x * width, y * H, point.categoryRank, pack);
}
const statusCoordinates = [[623 / W, 234 / H], [1056 / W, 408 / H]];
const statusCoordinatesSmall = [[.36, .37], [.73, .52]];
function statusPose(point, width, compact) {
  const [cx, cy] = (compact ? statusCoordinatesSmall : statusCoordinates)[point.status];
  const pack = Math.min(compact ? 16 : 23, width * (compact ? .14 : .13) / Math.sqrt(Math.max(1, point.statusCount)));
  return spiralPoint(cx * width, cy * H, point.statusRank, pack);
}
function spiralPoint(cx, cy, index, gap) {
  const [x, y] = polar(0, 0, gap * Math.sqrt(index), index * 2.399963);
  return [cx + x, cy + y * .62];
}
function chronPose(point, width, compact, days) {
  const pole = atlasPose(0, width, compact);
  const step = Math.min(.082, .37 / Math.max(1, point.dayCount - 1));
  return polar(pole.x, pole.y, pole.radius * (.68 - step * point.dayRank), traceDaysAngle(point.day, days));
}

function Record({ timeline, width, compact, point, trace }) {
  const start = chronPose(point, width, compact, trace.days);
  const group = catPose(point, width, compact, trace.labels.length);
  const end = statusPose(point, width, compact);
  // Camera begins immediately. Case glyphs remain briefly at each end
  // of each leg so the lead also works when the user scrubs backwards.
  const stagger = (point.index % 3) * .023;
  const delay = .14 + stagger;
  const movement = p => ease((p - delay) / (1 - 2 * delay));
  const position = (p, k) => {
    const forward = p < 1;
    const from = forward ? start : group, to = forward ? group : end;
    const t = movement(forward ? p : p - 1);
    return mix(from[k], to[k], t) - (k === 1 ? Math.sin(t * Math.PI) * (compact ? 6 : 9) : 0);
  };
  const x = useTransform(timeline, p => position(p, 0));
  const y = useTransform(timeline, p => position(p, 1));
  const r = compact ? 4.7 : 6.1;
  const onDateScale = Math.max(.19, Math.min(1, atlasPose(0, width, compact).radius * .39 /
    Math.max(1, point.dayCount - 1) / (r * 2.43)));
  const steadyScale = trace.points.length > 48 ? .64 : 1;
  const scale = useTransform(timeline, [0, .68, 1, 2], [Math.min(steadyScale, onDateScale), steadyScale, steadyScale, steadyScale]);
  return <motion.g style={{ x, y, scale, originX: '0px', originY: '0px' }} data-trace-index={point.index}
      data-trace-day={point.day} data-trace-category={point.category} data-trace-status={point.status}
      className={'hg-sky-record' + (point.status === 0 ? ' is-noted' : '')}>
    <path d={`M0 ${-r}L${r} 0L0 ${r}L${-r} 0Z`} />
  </motion.g>;
}

export default function HuiguanInstrument({ stats, timeline, season, dewMist = false, materialOverlay = null, rain = false, rainReduced = false, rainPaused = false }) {
  const measure = useRef(null);
  const [screen, setScreen] = useState({ width: W, compact: false });
  const id = useId().replace(/[^a-z0-9]/gi, 'h');
  const atmosphereMask = id + '-atmosphere';
  const glow = { aura: id + 'aura', bloom: id + 'bloom', ray: id + 'ray', wake: id + 'wake' };
  useLayoutEffect(() => {
    const element = measure.current;
    const read = () => {
      const rect = element.getBoundingClientRect();
      if (!rect.height || !rect.width) return;
      const next = { width: Math.round(rect.width * H / rect.height), compact: rect.width < 690 || rect.width / rect.height < 1.02 };
      setScreen(s => s.width === next.width && s.compact === next.compact ? s : next);
    };
    read();
    if (!globalThis.ResizeObserver) return;
    const watcher = new ResizeObserver(read);
    watcher.observe(element);
    return () => watcher.disconnect();
  }, []);
  const { width, compact } = screen;
  const trace = useMemo(() => buildHuiguanTrace(stats.marks, stats.dayCount, stats.categories), [stats]);
  const night = useTransform(timeline, [0, .15, .89, 1.1, 1.87, 2], ['0%', '0%', '100%', '100%', '0%', '0%']);
  // The shared transition still owns the camera. Only the outer atlas layer
  // breathes at rest, and the sway contracts through each page transition.
  const idle = useTransform(timeline, [0, .11, .89, 1, 1.11, 1.89, 2], [1, .25, .25, 1, .25, .25, 1]);
  // On the overview both the date scale and case glyphs share the atlas's
  // translation. Unlink the data once it leaves the ring to meet its labels.
  const overviewIdle = useTransform(timeline, [0, .11, .23, 2], [1, .25, 0, 0]);

  return <motion.div ref={measure} className="hg-sky-stage hg-instrument" data-sky-mode={season?.mode || 'week'}
      style={{ '--hg-sky-night': night, '--hg-atlas-rest': idle, '--hg-trace-rest': overviewIdle }} aria-hidden="true">
    <style>{skyStyle}</style>
    <svg className="hg-sky-svg" viewBox={`0 0 ${width} ${H}`}>
      <defs>
        {dewMist && <AtmosphereEdgeMask id={atmosphereMask} width={width} height={H}/>}
        <radialGradient id={glow.wake}>
          <stop stopColor="var(--hg-wake-color)" stopOpacity=".66" />
          <stop offset=".34" stopColor="var(--hg-wake-color)" stopOpacity=".32" />
          <stop offset=".73" stopColor="var(--hg-wake-color)" stopOpacity=".072" />
          <stop offset="1" stopColor="var(--hg-wake-color)" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={glow.aura}>
          <stop stopColor="var(--hg-dipper-aura)" stopOpacity=".27" />
          <stop offset=".37" stopColor="var(--hg-dipper-aura)" stopOpacity=".125" />
          <stop offset=".74" stopColor="var(--hg-dipper-aura)" stopOpacity=".033" />
          <stop offset="1" stopColor="var(--hg-dipper-aura)" stopOpacity="0" />
        </radialGradient>
        <radialGradient id={glow.bloom}>
          <stop stopColor="#fffae8" stopOpacity=".82" />
          <stop offset=".26" stopColor="#ffecbf" stopOpacity=".56" />
          <stop offset=".54" stopColor="var(--hg-dipper-bloom)" stopOpacity=".23" />
          <stop offset="1" stopColor="var(--hg-dipper-bloom)" stopOpacity="0" />
        </radialGradient>
        <linearGradient id={glow.ray}>
          <stop stopColor="var(--hg-dipper-bloom)" stopOpacity="0" />
          <stop offset=".36" stopColor="#ffeac4" stopOpacity=".15" />
          <stop offset=".50" stopColor="#fffaec" stopOpacity=".88" />
          <stop offset=".64" stopColor="#ffeac4" stopOpacity=".15" />
          <stop offset="1" stopColor="var(--hg-dipper-bloom)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {rain ? <>
        <HuiguanRainOptics id={id} width={width} height={H} timeline={timeline}
          reduced={rainReduced} paused={rainPaused}
          atlas={<g id={id+'chart'} className="hg-atlas-idle">
            <Atlas timeline={timeline} width={width} compact={compact} glow={glow} labels={false}/>
          </g>} />
        <g className="hg-atlas-idle">
          <Atlas timeline={timeline} width={width} compact={compact} glow={glow} onlyLabels />
        </g>
      </> : <g className="hg-atlas-idle"><Atlas timeline={timeline} width={width} compact={compact} glow={glow} /></g>}
      {dewMist && <g aria-hidden="true" pointerEvents="none" data-hg-atmosphere-surface="atlas" mask={`url(#${atmosphereMask})`}>
        <HuiguanDewMist timeline={timeline} width={width} height={H}/>
      </g>}
      {materialOverlay && <foreignObject x="0" y="0" width={width} height={H} aria-hidden="true" pointerEvents="none">
        <div xmlns="http://www.w3.org/1999/xhtml" data-hg-atmosphere-surface="instrument"
          style={{position:'relative',width:'100%',height:'100%',pointerEvents:'none',...atmosphereEdges}}>
          {materialOverlay}
        </div>
      </foreignObject>}
      <g className="hg-trace-lift">
        <DateScale timeline={timeline} width={width} compact={compact} days={trace.days} />
        {trace.points.map(point => <Record key={point.index} timeline={timeline} width={width} compact={compact} point={point} trace={trace} />)}
      </g>
    </svg>
  </motion.div>;
}
