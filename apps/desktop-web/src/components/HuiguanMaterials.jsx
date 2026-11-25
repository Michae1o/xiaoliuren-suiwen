import { useEffect, useId } from 'react';
import { animate, motion, useMotionValue, useTransform } from 'motion/react';
import { materialPhotos, snow } from '../data/huiguanMaterialAssets.js';
import css from '../styles/huiguan-materials.css?inline';

const clamp=(n,x=0,y=1)=>Math.max(x,Math.min(y,n));
const mix=(a,b,t)=>a+(b-a)*t;
const lerp=(timeline,a=0,b=1)=>clamp((timeline.get()-a)/(b-a));
const shade=timeline=>1-Math.abs(clamp(timeline.get(),0,2)-1);

function useArrival(reduced,duration,ease) {
  const p=useMotionValue(reduced?1:0);
  useEffect(()=>{
    if(reduced){p.jump(1);return;}
    const animation=animate(p,1,{duration,ease});
    return ()=>animation.stop();
  },[p,reduced,duration,ease]);
  return p;
}
const settle=[.18,.78,.31,1], lift=[.27,.09,.39,1], drive=[.19,.68,.23,1], freeze=[.32,.16,.41,1];

// Selected paper, plant and frost details retain their original photographic alpha.
function PhotoPatch({name,className,style}) {
  const cut=materialPhotos[name];
  const id='hg-weather-'+useId().replace(/[^a-z\d]/gi,'');
  const photoFilter=name.startsWith('frost-');
  return <motion.svg className={'hg-material-patch '+className} data-hg-material-layer={name}
    viewBox={`0 0 ${cut.size.join(' ')}`} style={{aspectRatio:cut.ratio,...style}}
    aria-hidden="true" focusable="false" pointerEvents="none">
    {photoFilter&&<defs><filter id={id} x="0" y="0" width="100%" height="100%" colorInterpolationFilters="sRGB">
      <feComponentTransfer><feFuncA type="linear" slope="1.07" intercept="-.042"/></feComponentTransfer>
      <feColorMatrix type="saturate" values=".42"/>
    </filter></defs>}
    <image href={cut.url} width={cut.size[0]} height={cut.size[1]} pointerEvents="none"
      filter={photoFilter?'url(#'+id+')':undefined}/>
  </motion.svg>;
}

export function SpringPaperBackdrop({timeline,reduced}) {
  const p=useArrival(reduced,1.53,settle);
  const x=useTransform(()=>mix(-9,0,p.get()));
  const y=useTransform(()=>mix(3,0,p.get())-lerp(timeline,1,2)*2.3);
  const opacity=useTransform(()=>p.get()*(.56-.12*shade(timeline)));
  const filter=useTransform(()=>`brightness(${1-.18*shade(timeline)}) saturate(.8)`);
  return <div className="hg-material-layer hg-material-paper-bleed"
      data-hg-material="spring" aria-hidden="true">
    <style>{css}</style>
    <PhotoPatch name="fibres" className="hg-material-spring-image" style={{x,y,opacity,filter}}/>
  </div>;
}
function GrainAwns({timeline,reduced}) {
  const p=useArrival(reduced,1.69,lift);
  const bow=()=>Math.sin(p.get()*Math.PI);
  const x=useTransform(()=>mix(11,0,p.get())-lerp(timeline)*3.4);
  const y=useTransform(()=>mix(5,0,p.get())+lerp(timeline,1,2)*2.5);
  const rotate=useTransform(()=>mix(2.1,0,p.get())-bow()*1.7);
  // The stem cluster comes in from the side as one photographic field; no glow panel.
  const opacity=useTransform(()=>p.get()*(.52-.08*shade(timeline))+bow()*.08);
  return <PhotoPatch name="awns" className="hg-material-grain-image" style={{x,y,rotate,opacity}}/>;
}
function FrostMargin({timeline,progress,name,lag,weight}){
  const age=()=>clamp((progress.get()-lag)/(1-lag));
  const opacity=useTransform(()=>(mix(.03,.47,age())-.15*shade(timeline)*age())*weight);
  const tone=useTransform(()=>`brightness(${mix(.77,1.03,shade(timeline))}) blur(.43px)`);
  const grow=useTransform(()=>`${mix(-18,103,age())}%`);
  return <PhotoPatch name={name} className={'hg-material-lens-frost hg-material-'+name}
    style={{opacity,filter:tone,'--hgw-frost-set':grow}}/>;
}
function CastFrost({timeline,reduced}) {
  const ice=useArrival(reduced,3.14,freeze);
  // Three independent photographed macro crops form small, soft crystals
  // around more of the actual lens rim. No centre fill or floating flakes.
  return <>
    <FrostMargin timeline={timeline} progress={ice} name="frost-side" lag={0} weight={.85}/>
    <FrostMargin timeline={timeline} progress={ice} name="frost-sill" lag={.12} weight={.88}/>
    <FrostMargin timeline={timeline} progress={ice} name="frost-corner" lag={.24} weight={.78}/>
  </>;
}
export function MaterialWeatherBackdrop({type,timeline,reduced}) {
  return <div className="hg-material-layer hg-material-weather-backdrop"
      data-hg-material={type} aria-hidden="true">
    <style>{css}</style>
    <CastFrost timeline={timeline} reduced={reduced}/>
  </div>;
}
function SnowGrain({timeline,reduced,near=false}) {
  const p=useArrival(reduced,near?2.32:2.71,drive);
  const drift=useTransform(()=>`${mix(near?21:11,near?-3:0,p.get())-lerp(timeline)*(near?7:3)+lerp(timeline,1,2)*2}%`);
  const fall=useTransform(()=>`${mix(near?-6:-3,near?4:2,p.get())+lerp(timeline,1,2)*(near?7:3)}%`);
  const layer=useTransform(()=>clamp(p.get()*(near?.74:.43)*(1+.16*shade(timeline))));
  return <motion.img alt="" draggable={false} src={snow}
    data-hg-material-layer={near?'foreground-gust':'distant-flurry'}
    className={`hg-material-image hg-material-snow-image ${near?'hg-material-snow-near':'hg-material-snow-far'}`}
    style={{x:drift,y:fall,opacity:layer}}/>;
}
function DeepSnow({timeline,reduced}) {
  const white=useTransform(()=>.54+.27*shade(timeline));
  return <div className="hg-material-snow-envelope">
    <motion.div className="hg-material-wash hg-material-snow-wash" style={{opacity:white}}/>
    <SnowGrain timeline={timeline} reduced={reduced}/>
    <SnowGrain timeline={timeline} reduced={reduced} near/>
  </div>;
}
const effect={grain:GrainAwns,snow:DeepSnow};
export default function HuiguanMaterials({type,timeline,reduced}) {
  const Material=effect[type];
  if(!Material) return null;
  return <div className="hg-material-layer" data-hg-material={type} aria-hidden="true">
    <style>{css}</style>
    <Material timeline={timeline} reduced={reduced}/>
  </div>;
}
