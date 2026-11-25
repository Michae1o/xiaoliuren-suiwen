import { Fragment, jsx, jsxs } from 'react/jsx-runtime';
import { motion, useTransform } from 'motion/react';
import { useSeasonArrival } from './useSeasonArrival.js';
import { seasonPhotos } from '../data/huiguanSeasonAssets.js';
import style from '../styles/huiguan-spring-material.css?inline';

const clamp = x=>Math.max(0, Math.min(1, x));
const nightWeight = t=>clamp(1-Math.abs(t.get()-1));

function Image({kind, part='main', style, skin}) {
  const {size, href, parts}=seasonPhotos[kind], box=parts[part];
  if (!box) return null;
  return jsx(motion.svg,{
    className:'hg-spring-photo-photo hg-spring-photo-'+kind+'-'+part+(skin?' '+skin:''),
    'aria-hidden':'true',focusable:'false','data-hg-spring-photo-part':kind+'-'+part,
    viewBox:box.join(' '), style:{aspectRatio:box[2]+'/'+box[3],...style},
    children:jsx('image',{width:size[0],height:size[1],href})
  });
}

function Swallows({timeline,reduced}) {
  const phase=useSeasonArrival(reduced,4.15);
  const x=useTransform(phase,[0,.18,.75,1],['-39vw','-31vw','-9vw','1.5vw']);
  const y=useTransform(phase,[0,.24,.58,1],['2vh','-.8vh','-2.4vh','-2.9vh']);
  const ratio=useTransform(phase,[0,.3,1],[.91,.96,1]);
  const feather=useTransform(()=>(
    (reduced?.81:.20+.63*phase.get())*(1-.05*nightWeight(timeline))
  ));
  const tone=useTransform(()=>`brightness(${1-.04*nightWeight(timeline)}) contrast(1.03) saturate(.64)`);
  const halo=useTransform(()=>.10+.38*nightWeight(timeline));
  return jsxs(motion.div,{
    className:'hg-spring-photo-flight', 'data-hg-spring-photo-cue':'level-glide',style:{x,y,scale:ratio},
    children:[
      jsx(Image,{kind:'swallows',style:{opacity:halo},skin:'hg-spring-photo-flight-rim'}),
      jsx(Image,{kind:'swallows',style:{opacity:feather,filter:tone}})
    ]
  });
}

function Blossoms({timeline,reduced}) {
  const phase=useSeasonArrival(reduced,2.8);
  const climb=useTransform(phase,[0,1],[6,0]);
  const warm=useTransform(()=>.53+.22*phase.get()-.11*nightWeight(timeline));
  const shade=useTransform(()=>`brightness(${.86+.18*nightWeight(timeline)}) saturate(.9)`);
  // Short prismatic catchlight is edge-limited and has no static rainbow band.
  const glass=useTransform(phase,[0,.15,.37,.53,.72,1],[0,0,.24,.30,.09,0]);
  const slide=useTransform(phase,[0,1],[-12,18]);
  return jsxs(Fragment,{children:[
    jsx(Image,{kind:'blossoms',style:{opacity:warm,y:climb,filter:shade}}),
    jsx(motion.div,{className:'hg-spring-photo-prism','data-hg-spring-photo-cue':'prism',style:{x:slide,opacity:glass}})
  ]});
}

function Cloudbreak({timeline,reduced}) {
  const phase=useSeasonArrival(reduced,4.6);
  const swift=useTransform(phase,[0,.32,.72,1],['6%','0%','-3%','-5%']);
  const opening=useTransform(phase,[0,.48,1],['35%','60%','97%']);
  const cloud=useTransform(()=>.47+.23*phase.get()+.03*nightWeight(timeline));
  const shade=useTransform(()=>`brightness(${.84+.23*nightWeight(timeline)}) saturate(${.92-.12*nightWeight(timeline)})`);
  const sun=useTransform(phase,[0,.42,.78,1],[.44,.28,.12,.04]);
  const warmBank=useTransform(phase,[0,.23,.67,1],[.61,.51,.16,.07]);
  const warmDrift=useTransform(phase,[0,1],['-2%','12%']);
  return jsxs(Fragment,{children:[
    jsx(Image,{kind:'cloudbreak',style:{opacity:cloud,x:swift,filter:shade,'--hg-spring-photo-aperture':opening}}),
    jsx(Image,{kind:'cloudbreak',part:'warm',style:{opacity:warmBank,x:warmDrift}}),
    jsx(motion.div,{className:'hg-spring-photo-warm-residue','data-hg-spring-photo-cue':'warmth-recedes',style:{opacity:sun}})
  ]});
}

export default function SpringSeasonMaterials({name,timeline,reduced}) {
  const Study={swallows:Swallows,blossoms:Blossoms,cloudbreak:Cloudbreak}[name];
  return jsxs('div',{
    className:'hg-spring-photo-backdrop hg-spring-photo-scope-'+name, 'data-hg-material':name, 'aria-hidden':'true',
    children:[jsx('style',{children:style}),jsx(Study,{timeline,reduced})]
  });
}
