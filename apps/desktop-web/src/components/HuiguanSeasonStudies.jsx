import { useEffect } from 'react';
import { animate,motion,useMotionValue,useTransform } from 'motion/react';
import { materialPhotos } from '../data/huiguanMaterialAssets.js';
import css from '../styles/huiguan-material-studies.css?inline';

const clamp=(v)=>Math.max(0,Math.min(1,v));
const mix=(a,b,t)=>a+(b-a)*t;
const night=timeline=>Math.max(0,1-Math.abs(timeline.get()-1));
const walk=timeline=>clamp(timeline.get()/2);
const ease=[.30,.12,.35,1];
function useShot(reduced,duration){
  const value=useMotionValue(reduced?1:0);
  useEffect(()=>{
    if(reduced){value.jump(1);return;}
    value.jump(0);
    const action=animate(value,1,{duration,ease});
    return ()=>action.stop();
  },[value,reduced,duration]);
  return value;
}
function useTone(timeline,a,b,sat=1){
  return useTransform(()=>`brightness(${mix(a,b,night(timeline))}) saturate(${sat})`);
}
function ShotPhoto({kind,part='main',style,name}){
  const asset=materialPhotos[kind+'-'+part];
  if(!asset)return null;
  return <motion.svg data-hg-study-layer={name||part}
    className={'hg-study-photo hg-study-'+kind+'-'+part} aria-hidden="true" focusable="false"
    viewBox={`0 0 ${asset.size.join(' ')}`} style={{aspectRatio:asset.ratio,...style}}>
    <image href={asset.url} width={asset.size[0]} height={asset.size[1]}/>
  </motion.svg>;
}

// Distant lightning is photographed as bounced light in cloud, not as a
// bolt or a screen-wide flash. One low-contrast pulse, then still sky.
function ThunderCloud({timeline,reduced}){
  const p=useShot(reduced,3.45);
  const drift=useTransform(()=>mix(13,0,p.get())-walk(timeline)*7);
  const haze=useTransform(()=>mix(.34,.59,p.get())-.05*night(timeline));
  const pulse=useTransform(p,[0,.20,.38,.55,.76,1],[.07,.11,.34,.21,.08,.07]);
  const color=useTone(timeline,.74,1.03,.68);
  return <>
    <ShotPhoto kind="thunder" style={{x:drift,opacity:haze,filter:color}} name="distant-cloud"/>
    <motion.div className="hg-study-thunder-bounce" data-hg-study-layer="cloud-bounce" style={{opacity:pulse}}/>
  </>;
}

// A low patch of real water with a time-staggered photographic ring expanding
// from the actual contact point. No lens marks or full-window rain.
function WaterRings({timeline,reduced}){
  const p=useShot(reduced,3.2);
  const quiet=useTransform(()=>mix(.34,.58,p.get())-.11*night(timeline));
  const pooled=useTransform(()=>mix(.85,1,walk(timeline)));
  const ringBase=useTransform(p,[0,.15,.36,.68,1],[0,.22,.69,.56,.40]);
  const ring=useTransform(()=>ringBase.get()*(1-.25*night(timeline)));
  const lower=useTransform(()=>17*night(timeline));
  const spread=useTransform(p,[0,.17,.67,1],[.59,.64,1,1.12]);
  const contact=useTransform(p,[0,.08,.13,.22,.34,1],[0,0,.34,.52,.02,0]);
  const fallBase=useTransform(p,[0,.09,.21,.3,1],[-25,-21,0,3,3]);
  const fall=useTransform(()=>fallBase.get()+lower.get());
  const color=useTone(timeline,.76,1.04,.76);
  return <>
    <ShotPhoto kind="ripples" style={{opacity:quiet,scaleX:pooled,filter:color}} name="water-level"/>
    <ShotPhoto kind="ripples" part="ring" style={{opacity:ring,scale:spread,y:lower,filter:color}} name="expanding-ripple"/>
    <motion.div className="hg-study-ripple-contact" data-hg-study-layer="water-contact" style={{opacity:contact,y:fall}}/>
  </>;
}

// Two different actual canopy crops. The deep plane is defocused already,
// the near plane lets just a little green flicker across an upper edge.
function SummerCanopy({timeline,reduced}){
  const p=useShot(reduced,2.85);
  const airy=useTransform(()=>mix(.32,.65,p.get())-.14*night(timeline));
  const shade=useTransform(()=>mix(.23,.49,p.get())-.09*night(timeline));
  const far=useTransform(()=>mix(-18,0,p.get())+walk(timeline)*9);
  const near=useTransform(()=>mix(13,0,p.get())-walk(timeline)*12);
  const sway=useTransform(()=>Math.sin(p.get()*Math.PI)*2.7);
  const color=useTone(timeline,.79,1.04,.78);
  return <>
    <ShotPhoto kind="canopy" part="far" style={{x:far,opacity:shade,filter:color}} name="far-canopy"/>
    <ShotPhoto kind="canopy" style={{x:near,y:sway,opacity:airy,filter:color}} name="young-leaf-light"/>
  </>;
}

function FillingGrain({timeline,reduced}){
  const p=useShot(reduced,3.0), phase=(delay)=>clamp((p.get()-delay)/(1-delay));
  const close=useTransform(()=>.71*phase(.08)-.16*night(timeline)*phase(.08));
  const farOpacity=useTransform(()=>.49*phase(.25)-.12*night(timeline)*phase(.25));
  const x=useTransform(()=>mix(-12,0,phase(.08))+walk(timeline)*3);
  const nearTilt=useTransform(()=>mix(-2.9,.5,phase(.08))+Math.sin(phase(.08)*Math.PI)*2.2);
  const farTilt=useTransform(()=>mix(4.2,0,phase(.25)));
  const color=useTone(timeline,.75,1.03,.87);
  return <>
    <ShotPhoto kind="grainfill" part="far" style={{rotate:farTilt,opacity:farOpacity,filter:color}} name="distant-green-head"/>
    <ShotPhoto kind="grainfill" style={{x,rotate:nearTilt,opacity:close,filter:color}} name="milky-green-grain"/>
  </>;
}
const effects={thunder:ThunderCloud,ripples:WaterRings,canopy:SummerCanopy,grainfill:FillingGrain};
export default function HuiguanSeasonStudies({type,timeline,reduced}){
  const Study=effects[type];
  return <div className={'hg-study-backdrop hg-study-scope-'+type}
      data-hg-material={type} aria-hidden="true">
    <style>{css}</style><Study timeline={timeline} reduced={reduced}/>
  </div>;
}
