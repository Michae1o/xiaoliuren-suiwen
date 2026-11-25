import { useLayoutEffect, useRef } from 'react';
import atlas from '../data/huiguan-atlas/song-1052.json';
import { atmosphereEdges } from './HuiguanAtmosphereEdges.jsx';

const clamp = (n,low=0,high=1) => Math.min(high,Math.max(low,n));
const smooth = n => { const x=clamp(n); return x*x*(3-2*x); };
const mix = (a,b,t) => a+(b-a)*t;
const TAU = Math.PI*2;
const color = (rgb,alpha) => `rgba(${rgb.join(',')},${clamp(alpha)})`;
const skySize = 392; // The atlas' original normalized viewBox radius.
const brightStars = Object.values(atlas.stars).filter(star => star.mag<=3.7);
const waterLinks = [...atlas.mansions,...atlas.enclosures].flatMap(figure =>
  figure.lines.flatMap(line => line.slice(1).map((star,i) => [atlas.stars[line[i]],atlas.stars[star]]))
).filter(([a,b]) => a && b && Math.min(a.mag,b.mag)<=3.4);

function soft(ctx,x,y,r,rgb,alpha,aspect) {
  ctx.save(); ctx.translate(x,y); ctx.scale(1,aspect);
  const gradient=ctx.createRadialGradient(0,0,0,0,0,r);
  gradient.addColorStop(0,color(rgb,alpha)); gradient.addColorStop(.52,color(rgb,alpha*.32));
  gradient.addColorStop(1,color(rgb,0)); ctx.fillStyle=gradient;
  ctx.fillRect(-r,-r,2*r,2*r); ctx.restore();
}

function paintWater(ctx,w,h,now,rings,host,rect,night,still) {
  const waterline=mix(.545,.58,night)*h, rgb=night>.45?[181,211,219]:[79,137,151];
  const middle=w*.63, glow=Math.max(120,w*.45);
  soft(ctx,middle,waterline+h*.105,glow,rgb,.085,Math.min(.31,h*.135/glow));
  function bend(x,y) {
    let total=0;
    for (const ring of rings) {
      const age=(now-ring.at)/1000;
      const offset=Math.hypot(x-ring.x,(y-ring.y)/.38)-(16+age*149);
      if (Math.abs(offset)<30) total+=Math.sin(offset*.26)*(1-age*.57)*3.8;
    }
    return clamp(total,-6,6);
  }

  // Reflect actual visible chart coordinates, not arbitrary decorative stars.
  const ctm=host.querySelector('.hg-atlas-camera')?.getScreenCTM?.();
  if (ctm) {
    const reflect=star => {
      const x=star.x*skySize,y=star.y*skySize;
      const sx=ctm.a*x+ctm.c*y+ctm.e-rect.left, sy=ctm.b*x+ctm.d*y+ctm.f-rect.top;
      const depth=(waterline-sy)/h;
      if (depth<.022 || depth>.27 || sx<0 || sx>w) return null;
      const ry=waterline+depth*h*.61, refract=bend(sx,ry);
      const edge=smooth(depth/.065)*(1-smooth((depth-.07)/.2))*smooth(sx/(w*.13))*smooth((w-sx)/(w*.13));
      return {x:sx+.83*refract,y:ry+.28*refract,edge};
    };
    for (const [a,b] of waterLinks) {
      const p=reflect(a),q=reflect(b);
      if (!p || !q) continue;
      const alpha=Math.min(p.edge,q.edge)*.21;
      if (alpha<.005) continue;
      ctx.beginPath(); ctx.moveTo(p.x,p.y); ctx.lineTo(q.x,q.y);
      ctx.strokeStyle=color(rgb,alpha); ctx.lineWidth=.67; ctx.stroke();
    }
    for (const star of brightStars) {
      const p=reflect(star);
      if (!p || p.edge*.33<.007) continue;
      ctx.beginPath(); ctx.arc(p.x,p.y,star.mag<1.3?1:.64,0,TAU);
      ctx.fillStyle=color(rgb,p.edge*.33); ctx.fill();
    }
  }
  for (const ring of rings) {
    const age=(now-ring.at)/1000, extent=12+age*148, opacity=(1-smooth(age/1.56))*.53;
    for (let i=0;i<3;i++) {
      const r=extent-i*9;
      if (r<=3) continue;
      ctx.beginPath(); ctx.ellipse(ring.x,ring.y,r,r*.34,-.04,.03,TAU-.13);
      ctx.lineWidth=i===0?.95:.65;ctx.strokeStyle=color(rgb,opacity*(1-i*.21));ctx.stroke();
    }
  }
  if (!rings.length && still) {
    ctx.beginPath();ctx.ellipse(middle,waterline+h*.09,Math.min(34,w*.07),6,0,.15,Math.PI*.96);
    ctx.lineWidth=.68; ctx.strokeStyle=color(rgb,.14); ctx.stroke();
  }
}

export default function HuiguanWaterReflection({timeline,reduced,paused}) {
  const ref=useRef(null);
  useLayoutEffect(() => {
    const canvas=ref.current, host=canvas.parentElement, ctx=canvas.getContext('2d');
    if (!ctx || paused) return;
    const media=window.matchMedia?.('(prefers-reduced-motion: reduce)');
    const isStill=()=>reduced||media?.matches;
    let w=0,h=0,ratio=1,raf=0,lastPointerAt=-Infinity,rings=[];
    function request() {
      if (!raf && !document.hidden) raf=requestAnimationFrame(draw);
    }
    function size() {
      const rect=host.getBoundingClientRect();
      w=Math.round(rect.width);h=Math.round(rect.height);
      if (!w || !h) return;
      ratio=Math.min(devicePixelRatio||1,2);
      const rw=Math.ceil(w*ratio), rh=Math.ceil(h*ratio);
      if (canvas.width!==rw) canvas.width=rw;
      if (canvas.height!==rh) canvas.height=rh;
      request();
    }
    function draw(now) {
      raf=0;
      if (!w || !h || document.hidden) return;
      const still=isStill();
      rings=still?[]:rings.filter(ring=>now-ring.at<1720);
      const rect=host.getBoundingClientRect(), night=smooth(1-Math.abs(1-timeline.get())*1.7);
      ctx.setTransform(ratio,0,0,ratio,0,0);ctx.clearRect(0,0,w,h);
      paintWater(ctx,w,h,now,rings,host,rect,night,still);
      if (rings.length) request();
    }
    function point(e) {
      if (isStill() || document.hidden || e.isPrimary===false) return;
      const now=performance.now();
      if (now-lastPointerAt<34) return;
      lastPointerAt=now;
      const rect=host.getBoundingClientRect();
      rings.push({x:e.clientX-rect.left,y:e.clientY-rect.top,at:now});
      if (rings.length>19) rings.shift();
      request();
    }
    function visibility() {
      if (document.hidden) {
        if (raf) cancelAnimationFrame(raf);
        raf=0;rings=[];
      } else size();
    }
    function motionChanged() { rings=[];request(); }
    const observer=globalThis.ResizeObserver ? new ResizeObserver(size) : null;
    observer?.observe(host);
    window.addEventListener('resize',size);
    document.addEventListener('visibilitychange',visibility);
    host.addEventListener('pointermove',point,{passive:true});
    media?.addEventListener?.('change',motionChanged);
    const unsubscribe=timeline.on('change',request);
    size();
    return () => {
      if (raf) cancelAnimationFrame(raf);
      observer?.disconnect();unsubscribe();
      window.removeEventListener('resize',size);
      document.removeEventListener('visibilitychange',visibility);
      host.removeEventListener('pointermove',point);
      media?.removeEventListener?.('change',motionChanged);
    };
  },[timeline,reduced,paused]);
  return <canvas ref={ref} data-hg-atmosphere="qingming" aria-hidden="true"
    style={{position:'absolute',inset:0,width:'100%',height:'100%',zIndex:1,pointerEvents:'none',...atmosphereEdges}}/>;
}
