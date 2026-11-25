import { Fragment, jsx, jsxs } from 'react/jsx-runtime';
import { motion, useTransform } from 'motion/react';
import { useSeasonArrival } from './useSeasonArrival.js';
import { seasonPhotos } from '../data/huiguanSeasonAssets.js';
import style from '../styles/huiguan-autumn-material.css?inline';

const nightWeight=t=>Math.max(0,1-Math.abs(t.get()-1));
function Photo({name,part='main',style,layer}){
 const {size,href,parts}=seasonPhotos[name],box=parts[part];
 return jsx(motion.svg,{
  className:'hg-autumn-photo-photo hg-autumn-photo-'+name+'-'+part,'aria-hidden':'true',focusable:'false',
  'data-hg-autumn-photo-layer':layer||name+'-'+part,viewBox:box.join(' '),style:{aspectRatio:box[2]+'/'+box[3],...style},
  children:jsx('image',{width:size[0],height:size[1],href}),
 });
}
function Leaf({timeline,reduced}){
 const a=useSeasonArrival(reduced,4.9);
 const x=useTransform(a,[0,.14,.39,.63,.84,1],['5.5vw','-3vw','-9vw','-6.5vw','-12vw','-14vw']);
 const y=useTransform(a,[0,.13,.34,.60,.84,1],['-15vh','-7vh','5vh','16vh','29vh','32vh']);
 const r=useTransform(a,[0,.18,.39,.59,.80,1],[-31,-9,22,-17,28,16]);
 const fold=useTransform(a,[0,.17,.32,.54,.78,1],[-22,4,76,150,201,209]);
 const tilt=useTransform(a,[0,.30,.53,.75,1],[15,41,11,-23,-8]);
 const light=useTransform(a,[0,.13,.72,1],[.27,.93,.86,.82]);
 const tint=useTransform(()=>`saturate(${.82-.12*nightWeight(timeline)}) brightness(${.90+.18*nightWeight(timeline)})`);
 return jsx(motion.div,{className:'hg-autumn-photo-flight','data-hg-autumn-photo-cue':'leaf-descent',style:{x,y,rotate:r},
  children:jsx(Photo,{name:'turning-leaf',layer:'leaf-specimen',style:{rotateY:fold,rotateX:tilt,opacity:light,filter:tint}}),
 });
}
function Dew({timeline,reduced}){
 const a=useSeasonArrival(reduced,3.55);
 // Transparent photo carries the actual dew positions; a high-frequency
 // sidelight catches edge polish on those same droplets, never adds points.
 const still=useTransform(()=>.51+.24*a.get()+.04*nightWeight(timeline));
 const color=useTransform(()=>`brightness(${.90+.41*nightWeight(timeline)}) saturate(${.86-.16*nightWeight(timeline)})`);
 const shine=useTransform(a,[0,.12,.39,.63,.86,1],[.06,.11,.38,.27,.18,.12]);
 const reveal=useTransform(a,[0,1],['20%','82%']);
 return jsxs(Fragment,{children:[
  jsx(Photo,{name:'grass-dew',layer:'dew-clinging-to-stems',style:{opacity:still,filter:color}}),
  jsx(Photo,{name:'grass-dew',part:'light',layer:'on-stem-catchlight',style:{opacity:shine,'--hg-autumn-photo-glint':reveal}}),
 ]});
}
export default function AutumnSeasonMaterials({name,timeline,reduced}){
 const Study={'turning-leaf':Leaf,'grass-dew':Dew}[name];
 return jsxs('div',{
  className:'hg-autumn-photo-backdrop hg-autumn-photo-scope-'+name,'data-hg-material':name,'aria-hidden':'true',
  children:[jsx('style',{children:style}),jsx(Study,{timeline,reduced})],
 });
}
