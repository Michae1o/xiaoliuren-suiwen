import { useEffect, useId, useRef } from 'react';
import { Fragment, jsx, jsxs } from 'react/jsx-runtime';
import { motion, useTransform } from 'motion/react';
import { useSeasonArrival } from './useSeasonArrival.js';
import { seasonPhotos } from '../data/huiguanSeasonAssets.js';
import style from '../styles/huiguan-sun-material.css?inline';

const clamp=x=>Math.max(0,Math.min(1,x));
const nightWeight=t=>clamp(1-Math.abs(t.get()-1));
function Photo({name,part='main',style,layer,extra='',distort=null }){
 const photo=seasonPhotos[name],box=photo.parts[part];
 const ink=useId().replace(/[^a-z0-9]/gi,'h')+'-heat-refraction';
 const map=useRef(null);
 useEffect(()=>{
  if(!distort||!map.current)return;
  const node=map.current,put=n=>node.setAttribute('scale',String(n));
  put(distort.get());
  return distort.on('change',put);
 },[distort]);
 return jsx(motion.svg,{
  className:'hg-sun-photo-photo hg-sun-photo-'+name+'-'+part+' '+extra,
  'aria-hidden':'true',focusable:'false','data-hg-sun-photo-layer':layer||name+'-'+part,viewBox:box.join(' '),
  style:{aspectRatio:box[2]+'/'+box[3],...style},
  children:[
   distort&&jsx('defs',{children:jsxs('filter',{id:ink,filterUnits:'userSpaceOnUse',x:0,y:0,width:photo.size[0],height:photo.size[1],
    children:[jsx('feTurbulence',{type:'fractalNoise',baseFrequency:'.018 .095',numOctaves:1,seed:17,result:'warmAir'}),
      jsx('feDisplacementMap',{ref:map,in:'SourceGraphic',in2:'warmAir',scale:0,xChannelSelector:'R',yChannelSelector:'G','data-hg-sun-photo-optics':'heated-refractor'})]})}),
   jsx('image',{width:photo.size[0],height:photo.size[1],href:photo.href,filter:distort?'url(#'+ink+')':undefined}),
  ],
 });
}
// Two overlapping lights in the same dusk. Positions are fixed; only the
// light settles, so turning a report page cannot produce a color seam.
function Equinox({reduced}){
 const arrival=useSeasonArrival(reduced,3.05);
 const amber=useTransform(arrival,[0,.38,.73,1],[.36,.50,.48,.46]);
 const blue=useTransform(arrival,[0,.38,.73,1],[.49,.45,.55,.53]);
 const warmMist=useTransform(amber,alpha=>alpha*(.17/.46));
 const coolMist=useTransform(blue,alpha=>alpha*(.16/.53));
 return jsxs('div',{
  className:'hg-sun-photo-equinox-dusk','data-hg-sun-photo-cue':'balanced-dusk',
  children:[
   jsx(motion.div,{className:'hg-sun-photo-dusk-warm',style:{opacity:amber}}),
   jsx(motion.div,{className:'hg-sun-photo-dusk-cool',style:{opacity:blue}}),
   jsx(motion.div,{className:'hg-sun-photo-dusk-warm-mist',style:{opacity:warmMist}}),
   jsx(motion.div,{className:'hg-sun-photo-dusk-cool-mist',style:{opacity:coolMist}}),
  ],
 });
}
function Summer({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.05);
 const reach=useTransform(a,[0,.3,.69,1],['28%','49%','84%','105%']);
 const light=useTransform(()=>.28+.51*a.get()-.24*nightWeight(timeline));
 const beam=useTransform(()=>.18+.51*a.get()-.23*nightWeight(timeline));
 return jsxs(Fragment,{children:[
  jsx(motion.div,{className:'hg-sun-photo-solstice-light','data-hg-sun-photo-cue':'long-day-expands',style:{'--hg-sun-light-reach':reach,opacity:light}}),
  jsx(motion.div,{className:'hg-sun-photo-solstice-horizon','data-hg-sun-photo-layer':'solstice-day-spread',style:{opacity:beam,'--hg-sun-light-reach':reach}}),
 ]});
}
function Lotus({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.25);
 const sway=useTransform(a,[0,.19,.40,.67,1],[-5.5,1.7,-1.55,.85,0]);
 const x=useTransform(a,[0,1],[10,0]);
 const strength=useTransform(()=>.51+.27*a.get()-.10*nightWeight(timeline));
 const tone=useTransform(()=>`brightness(${.89+.25*nightWeight(timeline)}) saturate(.87)`);
 return jsx(Photo,{name:'lotus-cool',layer:'lotus-stem-and-leaf',style:{x,rotate:sway,opacity:strength,filter:tone}});
}
function Heat({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.65);
 const potency=useTransform(()=>.39+.34*a.get()-.07*nightWeight(timeline));
 const tone=useTransform(()=>`brightness(${.87+.21*nightWeight(timeline)}) saturate(.79)`);
 const x=useTransform(a,[0,.17,.38,.57,.82,1],[0,3.8,-2.7,2.8,-1.2,0]);
 const y=useTransform(a,[0,.17,.38,.57,.82,1],[0,-1.9,-.4,-1.4,-.4,0]);
 const stretch=useTransform(a,[0,.23,.43,.60,.82,1],[1,1.027,.99,1.022,1.005,1]);
 const shimmer=useTransform(a,[0,.19,.42,.60,.8,1],[.015,.49,.26,.39,.15,.08]);
 const refract=useTransform(a,[0,.16,.38,.59,.8,1],[0,8.7,3.4,6.8,1.8,0]);
 return jsxs(Fragment,{children:[
  jsx(Photo,{name:'heat-haze',part:'ground',layer:'baked-ground',style:{opacity:potency,filter:tone}}),
  jsx(Photo,{name:'heat-haze',part:'air',layer:'rising-refraction',distort:reduced?null:refract,style:{x,y,scaleY:stretch,opacity:shimmer}}),
 ]});
}
function Ice({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.15);
 const reach=useTransform(a,[0,.25,1],['14%','37%','96%']);
 const strength=useTransform(()=>.35+.40*a.get()+.02*nightWeight(timeline));
 const tone=useTransform(()=>`brightness(${.93+.24*nightWeight(timeline)}) saturate(.67)`);
 return jsx(Photo,{name:'first-ice',layer:'one-thin-frozen-waterline',
  style:{opacity:strength,filter:tone,'--hg-sun-ice-reach':reach}});
}
export default function SunSeasonMaterials({name,timeline,reduced}){
 const Study={'equinox-dusk':Equinox,'solstice-day':Summer,'lotus-cool':Lotus,'heat-haze':Heat,'first-ice':Ice}[name];
 return jsxs('div',{className:'hg-sun-photo-backdrop hg-sun-photo-scope-'+name,'data-hg-material':name,'aria-hidden':'true',
  children:[jsx('style',{children:style}),jsx(Study,{timeline,reduced})]});
}
