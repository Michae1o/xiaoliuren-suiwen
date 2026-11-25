import { useEffect, useRef } from 'react';
import {createRain,stepRain,staticRain,paintRain,paneDrops,paneShape,MAX_BEADS} from './weather-optics.js';

// Rain is sampled in CSS pixels. The atlas is in SVG units; do not move
// record marks, captions or mansion labels into the refracted layer.
export default function HuiguanRainOptics({id,atlas,width,height,timeline,reduced,paused}) {
  const ref=useRef(null);
  const model=useRef(null);
  const dry=useRef([]);
  const wet=useRef([]);
  const clips=useRef([]);
  const lenses=useRef([]);
  const hilites=useRef([]);
  useEffect(()=>{
    const c=ref.current, ctx=c?.getContext('2d',{alpha:true});
    if(!ctx)return;
    if(!model.current)model.current={w:1,h:1,state:createRain()};
    const m=model.current,s=m.state;
    let {w,h}=m,frame=0,last=null;
    const media=window.matchMedia?.('(prefers-reduced-motion: reduce)');
    const still=()=>reduced||!!media?.matches;
    const draw=()=>{
      if(document.hidden)return;
      const state=still()?staticRain(w,h):s;
      paintRain(ctx,state,w,h,Math.abs(timeline.get()-1)<.56);
      const drops=paneDrops(state);
      const sx=width/w, sy=height/h;
      for(let i=0;i<MAX_BEADS;i++){
        const d=drops[i], opacity=d?.alpha??0;
        dry.current[i]?.setAttribute('opacity',opacity);
        wet.current[i]?.setAttribute('opacity',opacity);
        if(!d||opacity<=0)continue;
        const values=paneShape(d.x*sx,d.y*sy,d.rx*sx,d.ry*sy,d.shape,d.impact);
        clips.current[i]?.setAttribute('d',values);
        dry.current[i]?.setAttribute('d',values);
        hilites.current[i]?.setAttribute('d',values);
        const x=d.x*sx,y=d.y*sy;
        // A curved lens inverts a small patch of the same live decorative chart.
        // Coordinates are pane-local, so atlas drift and page movement still match.
        lenses.current[i]?.setAttribute('transform',`translate(${x} ${y}) scale(-1.24 -1.13) translate(${-x} ${-y})`);
      }
    };
    const resize=()=>{
      const bounds=c.getBoundingClientRect();
      if(!bounds.width||!bounds.height)return;
      const oldW=w,oldH=h;w=bounds.width;h=bounds.height;m.w=w;m.h=h;
      const ratio=Math.min(window.devicePixelRatio||1,1.55,Math.sqrt(2200000/(w*h)));
      c.width=Math.max(1,Math.round(w*ratio));c.height=Math.max(1,Math.round(h*ratio));
      ctx.setTransform(c.width/w,0,0,c.height/h,0,0);
      for(const a of [...s.fall,...s.glass]){a.x*=w/oldW;a.y*=h/oldH;}
      for(const t of s.traces){t.x*=w/oldW;t.y*=h/oldH;t.dx*=w/oldW;t.dy*=h/oldH;}
      draw();
    };
    let painted=null;
    const tick=n=>{
      frame=0;
      if(document.hidden||paused||still())return;
      if(last!==null)stepRain(s,(n-last)/1000,w,h);
      last=n;
      if(painted===null||n-painted>=29){painted=n;draw();}
      frame=requestAnimationFrame(tick);
    };
    const visibility=()=>{
      if(frame)cancelAnimationFrame(frame);
      frame=0;last=null;painted=null;
      if(document.hidden)return;
      draw();
      if(!paused&&!still())frame=requestAnimationFrame(tick);
    };
    const size=window.ResizeObserver?new ResizeObserver(resize):null;
    size?.observe(c);resize();
    const change=timeline.on('change',()=>{if(still()||paused)draw()});
    document.addEventListener('visibilitychange',visibility);
    media?.addEventListener?.('change',visibility);visibility();
    return ()=>{
      if(frame)cancelAnimationFrame(frame);
      size?.disconnect();change();
      media?.removeEventListener?.('change',visibility);
      document.removeEventListener('visibilitychange',visibility);
    };
  },[timeline,reduced,paused,width,height]);

  const slots=Array.from({length:MAX_BEADS},(_,i)=>i);
  const key=i=>id+'wet'+i;
  return <g aria-hidden="true" pointerEvents="none" data-hg-lens-rain="" data-hg-material="rain">
    <defs>
      <mask id={id+'dry'} x="0" y="0" width={width} height={height} maskUnits="userSpaceOnUse">
        <rect width={width} height={height} fill="white"/>
        {slots.map(i=><path key={i} ref={v=>{dry.current[i]=v}} fill="black" opacity="0"/>)}
      </mask>
      {slots.map(i=><clipPath key={i} id={key(i)} clipPathUnits="userSpaceOnUse">
        <path ref={v=>{clips.current[i]=v}}/></clipPath>)}
      <radialGradient id={id+'pin'} cx=".29" cy=".23" r=".76">
        <stop stopColor="#c8eef0" stopOpacity=".37"/>
        <stop offset=".16" stopColor="#bbe4e8" stopOpacity=".19"/>
        <stop offset=".48" stopColor="#b8e0df" stopOpacity=".025"/>
        <stop offset=".75" stopColor="#132627" stopOpacity=".04"/>
        <stop offset="1" stopColor="#081615" stopOpacity=".25"/>
      </radialGradient>
    </defs>
    <g mask={`url(#${id}dry)`}>{atlas}</g>
    {slots.map(i=><g key={i} opacity="0" ref={v=>{wet.current[i]=v}}>
      <g clipPath={`url(#${key(i)})`}>
        <use href={'#'+id+'chart'} ref={v=>{lenses.current[i]=v}}/>
        <path ref={v=>{hilites.current[i]=v}} fill={`url(#${id}pin)`}/>
      </g>
    </g>)}
    <foreignObject width={width} height={height}>
      <canvas ref={ref} className="hg-material-rain-optics" data-hg-rain-mode={reduced?'still':'moving'}
        style={{display:'block',width:'100%',height:'100%'}} />
    </foreignObject>
  </g>;
}
