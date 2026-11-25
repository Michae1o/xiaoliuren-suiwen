import { Fragment, jsx, jsxs } from 'react/jsx-runtime';
import { motion, useTransform } from 'motion/react';
import { useSeasonArrival } from './useSeasonArrival.js';
import { seasonPhotos } from '../data/huiguanSeasonAssets.js';
import style from '../styles/huiguan-winter-material.css?inline';

const nightWeight=t=>Math.max(0,1-Math.abs(t.get()-1));
function Photo({name,part='main',style,cue}){
 const {size,href,parts}=seasonPhotos[name],box=parts[part];
 return jsx(motion.svg,{
  className:'hg-winter-photo-photo hg-winter-photo-'+name+'-'+part,'aria-hidden':'true',focusable:'false',
  'data-hg-winter-photo-layer':cue||name,viewBox:box.join(' '),style:{aspectRatio:box[2]+'/'+box[3],...style},
  children:jsx('image',{width:size[0],height:size[1],href})
 });
}
// Subtle soft focus describes snow in the air. The snowfall does not use
// vector flake symbols, star-shaped glows, or 大雪's lateral snow texture.
const fallPlan=[
 [7,10,1.5,31,3.8,.02], [15,29,2.2,28,-2.8,.18], [23,3,3.0,39,4.6,.06],
 [31,41,1.8,33,4.9,.32], [36,20,2.6,30,-2.5,.19], [41,8,1.2,46,4.4,.22],
 [48,32,2.1,23,3.0,.11], [53,12,3.0,43,-4.7,.29], [59,27,1.7,25,2.1,.03],
 [65,15,2.0,42,3.9,.26], [69,41,2.0,31,4.1,.21], [75,4,3.3,50,-2.4,.17],
 [82,34,1.7,25,-3.2,.06], [87,14,2.6,48,3.0,.25], [94,20,1.4,32,-4.1,.11],
];
function Flake({shot,timeline,spec,index}){
 const [x0,y0,radius,descent,wind,lag]=spec;
 const age=()=>Math.min(1,Math.max(0,(shot.get()-lag)/(1-lag)));
 const x=useTransform(()=>`${(age()*wind+Math.sin(age()*Math.PI*1.5)*.82).toFixed(2)}vw`);
 const y=useTransform(()=>`${(descent*age()).toFixed(2)}vh`);
 const opacity=useTransform(()=>{
   const p=age(), depth=.38+radius*.077;
   const left=Math.min(1,p/.23), fade=index%3===0 ? Math.max(0,.96-(p-.38)*.47) : Math.max(.28,1-(p-.63)*1.85);
   return .92*depth*left*fade*(1+.12*nightWeight(timeline));
 });
 return jsx(motion.i,{className:'hg-winter-photo-flake','data-hg-winter-photo-layer':'falling-flake',
  style:{left:x0+'%',top:y0+'%',width:radius*2.02+'px',height:radius*2.24+'px',x,y,opacity}});
}
function Snow({timeline,reduced}){
 const shot=useSeasonArrival(reduced,5.25);
 const pigment=useTransform(()=>nightWeight(timeline)>.47?'#e9f5ff':'#687d98');
 return jsx(motion.div,{className:'hg-winter-photo-snow-field','data-hg-winter-photo-cue':'small-snow-descends',
  style:{'--hg-winter-photo-flake-tone':pigment},
  children:fallPlan.map((spec,index)=>jsx(Flake,{shot,timeline,spec,index},index))});
}
function Dawn({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.15);
 // The luminous horizon stays low while a thin warm seam returns. The
 // chart's original dark page is not replaced with a full-screen flash.
 const rise=useTransform(a,[0,.18,.50,.84,1],[17,14,7,0,-1]);
 const open=useTransform(a,[0,.18,.48,.85,1],[.25,.33,.62,.91,1]);
 const heat=useTransform(()=>.12+.47*a.get()+.06*nightWeight(timeline));
 const horizon=useTransform(()=>.19+.58*a.get()-.06*nightWeight(timeline));
 return jsxs(Fragment,{children:[
  jsx(motion.div,{className:'hg-winter-photo-dawn-shadow',style:{opacity:heat}}),
  jsx(motion.div,{className:'hg-winter-photo-dawn-air','data-hg-winter-photo-layer':'low-warm-air',style:{y:rise,scaleX:open,opacity:heat}}),
  jsx(motion.div,{className:'hg-winter-photo-dawn-line','data-hg-winter-photo-layer':'short-day-return',style:{y:rise,scaleX:open,opacity:horizon}}),
 ]});
}
function Icicles({timeline,reduced}){
 const a=useSeasonArrival(reduced,3.6);
 const light=useTransform(()=>.34+.42*a.get()-.05*nightWeight(timeline));
 const refraction=useTransform(()=>`brightness(${.75+.45*nightWeight(timeline)}) saturate(.62)`);
 const open=useTransform(a,[0,.2,.58,1],['31%','44%','73%','107%']);
 return jsx(Photo,{name:'icicles',style:{opacity:light,filter:refraction,'--hg-winter-photo-ice-set':open},cue:'icicles-rooted-to-edge'});
}
function Freeze({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.30);
 const cool=useTransform(()=>.39+.32*a.get()-.06*nightWeight(timeline));
 const fog=useTransform(()=>.37+.32*a.get()-.14*nightWeight(timeline));
 const sheen=useTransform(()=>`brightness(${.86+.32*nightWeight(timeline)}) saturate(.75)`);
 const extent=useTransform(a,[0,.16,.56,1],['26%','40%','79%','108%']);
 return jsxs(Fragment,{children:[
  jsx(Photo,{name:'deep-ice',style:{opacity:cool,filter:sheen,'--hg-winter-photo-ice-set':extent},cue:'deep-frozen-cracks'}),
  jsx(motion.div,{className:'hg-winter-photo-deep-air','data-hg-winter-photo-layer':'deep-cold-reflection',style:{opacity:fog}}),
 ]});
}
export default function WinterSeasonMaterials({name,timeline,reduced}){
 const Study={'first-snow':Snow,'returning-dawn':Dawn,icicles:Icicles,'deep-ice':Freeze}[name];
 return jsxs('div',{className:'hg-winter-photo-backdrop hg-winter-photo-scope-'+name,'aria-hidden':'true',
  'data-hg-material':name,'data-hg-winter-photo-effect':name,
  children:[jsx('style',{children:style}),jsx(Study,{timeline,reduced})]});
}
