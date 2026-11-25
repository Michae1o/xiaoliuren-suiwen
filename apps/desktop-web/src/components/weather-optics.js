const clamp=(v,min=0,max=1)=>Math.max(min,Math.min(max,v));
const mix=(a,b,t)=>a+(b-a)*t;
export const MAX_FALLS=19,MAX_BEADS=6,MAX_TRACES=10;

export function createRain(rng=Math.random){
  return {rng,wait:.06,fall:[],glass:[],traces:[],counts:{missed:0,hit:0,slips:0,expired:0,watermarks:0,bareWatermarks:0}};
}
function spawn(s,w){
  const r=s.rng;
  // A natural, light rain across the pane. Central trails are softer so
  // the report remains legible. No fixed showcase drop in the app.
  const zone=mix(.075,.97,r());
  const interior=zone>.31&&zone<.73;
  const hit=r()<(interior?.20:.28);
  s.fall.push({x:w*zone,y:-12,v:mix(295,560,r()),gx:mix(-17,6,r()),
    r:mix(.85,1.92,r()),quiet:interior,door:hit?mix(.22,.74,r()):1.12,seed:r()});
}
export function stepRain(s,dt,w,h){
  dt=clamp(dt,0,.055);
  const r=s.rng;
  s.traces=s.traces.filter(t=>{if(t.released)t.age+=dt;return t.age<t.life});
  s.wait-=dt;
  while(s.wait<=0){
    if(s.fall.length<MAX_FALLS)spawn(s,w);
    s.wait+=mix(.088,.215,r());
  }
  s.fall=s.fall.filter(d=>{
    d.v+=78*dt;d.y+=d.v*dt;d.x+=d.gx*dt;
    if(d.y>=d.door*h){
      if(d.door<1&&s.glass.length<MAX_BEADS){
        const x=clamp(d.x,w*.063,w*.978), size=clamp(d.r*2.85+mix(.95,1.3,r()),3.7,6.8),
          linger=mix(1.7,4.05,r()), slip=r()<.48;
        s.glass.push({x,y:d.door*h,size,age:0,linger,slip,drop:slip?mix(21,43,r()):0,pause:mix(.33,1.1,r()),quiet:d.quiet,tilt:mix(-.34,.30,r()),shape:r(),counted:false});
        s.counts.hit++;
      }else s.counts.missed++;
      return false;
    }
    return true;
  });
  s.glass=s.glass.filter(b=>{
    b.age+=dt;
    if(b.slip&&!b.counted&&b.age>b.pause){b.counted=true;s.counts.slips++;}
    if(b.slip&&b.age>b.pause+.055&&b.age<b.linger){
      const pose=beadPose(b);
      if(!b.trace&&pose.y-b.y>1.7&&s.traces.length<MAX_TRACES){
        b.trace={x:b.x,y:b.y,dx:pose.x,dy:pose.y,age:0,life:mix(1.25,2.05,r()),quiet:b.quiet,thickness:mix(.90,1.48,r()),released:false};
        s.traces.push(b.trace);s.counts.watermarks++;
      }
      if(b.trace&&pose.y>b.trace.dy+.025){
        b.trace.dx=pose.x;b.trace.dy=pose.y;b.trace.age=0;
      }
    }
    if(b.age>b.linger){s.counts.expired++;if(b.trace&&s.traces.includes(b.trace)){b.trace.released=true;s.counts.bareWatermarks++;}return false;}
    return true;
  });
  return s;
}

export function beadPose(b){
  const t=clamp((b.age-b.pause)/1.03);
  const e=t*t*(3-2*t), fade=clamp((b.linger-b.age)/.66), appear=clamp(b.age/.095);
  return {x:b.x+b.tilt*e*2,y:b.y+e*b.drop,alpha:fade*appear,drag:Math.sin(Math.PI*e),impact:Math.sin(Math.PI*clamp(b.age/.24))*Math.exp(-b.age*3.4)};
}
export function staticRain(w,h){
  const beads=[[.184,.395,4.0,.32],[.438,.567,4.4,.56],[.674,.251,3.9,.74],[.859,.716,5.5,.21],[.958,.435,4.7,.28]];
  return {fall:[],traces:[],glass:beads.map(([x,y,size,shape])=>({x:x*w,y:y*h,size,shape,age:1,linger:4,pause:.7,drop:0,quiet:x>.31&&x<.73,tilt:shape-.5}))};
}
function rgba(dark,a){
  const c=dark?[193,217,230]:[58,89,107]; return `rgba(${c.join(',')},${clamp(a)})`;
}
export function paneDrops(s){
  return s.glass.map(b=>{
    const at=beadPose(b), moving=at.drag;
    return {x:at.x,y:at.y,rx:b.size*(1+.39*at.impact-.23*moving),
      ry:b.size*(.86-.27*at.impact+.54*moving),shape:b.shape,impact:at.impact,
      alpha:at.alpha*(b.quiet?.66:1)};
  });
}
export function paneShape(x,y,rx,ry,seed,impact){
  const points=[];
  for(let i=0;i<20;i++){
    const a=i*Math.PI/10;
    const r=1+.073*Math.sin(3*a+seed*6)+.049*Math.cos(5*a+seed*3)+impact*.072*Math.cos(2*a+1.9);
    const v=Math.sin(a)*r,u=Math.cos(a)*r*(1+.15*Math.max(0,v));
    points.push([x+rx*u,y+ry*v]);
  }
  const short=n=>n.toFixed(2), mid=(a,b)=>a.map((v,i)=>short((v+b[i])*.5)).join(' ');
  return 'M '+mid(points[19],points[0])+points.map((p,i)=>
    ' Q '+p.map(short).join(' ')+' '+mid(p,points[(i+1)%points.length])).join('')+' Z';
}
function paintWatermark(ctx,a,dark){
  if(a.dy-a.y<1.6)return;
  const f=Math.pow(clamp(1-a.age/a.life),1.7)*(a.quiet?.65:1), h=a.dy-a.y;
  const light=ctx.createLinearGradient(a.x,a.y,a.dx,a.dy);
  light.addColorStop(0,rgba(dark,.018*f));light.addColorStop(.48,rgba(dark,.25*f));
  light.addColorStop(.84,rgba(dark,.32*f));light.addColorStop(1,rgba(dark,.092*f));
  ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.quadraticCurveTo(a.x-.6,a.y+h*.54,a.dx,a.dy);
  ctx.strokeStyle=light;ctx.lineWidth=a.thickness;ctx.lineCap='round';ctx.stroke();
  ctx.beginPath();ctx.moveTo(a.x+.55,a.y+h*.14);ctx.quadraticCurveTo(a.x+.2,a.y+h*.55,a.dx+.65,a.dy);
  ctx.strokeStyle=`rgba(231,247,252,${.105*f})`;ctx.lineWidth=.38;ctx.stroke();
}
export function paintRain(ctx,s,w,h,dark){
  ctx.clearRect(0,0,w,h);
  for(const d of s.fall){
    if(d.y<0||d.y>h+1)continue;
    const trail=mix(9,25,d.seed), dx=d.gx*.033;
    const light=ctx.createLinearGradient(d.x-dx,d.y-trail,d.x,d.y+1);
    light.addColorStop(0,rgba(dark,0));
    light.addColorStop(.62,rgba(dark,.17));
    light.addColorStop(1,rgba(dark,d.quiet?.40:.58));
    ctx.beginPath();ctx.moveTo(d.x-dx,d.y-trail);ctx.lineTo(d.x,d.y+1);
    ctx.strokeStyle=light;ctx.lineWidth=d.r*.82;ctx.lineCap='round';ctx.stroke();
  }
  for(const m of s.traces||[])paintWatermark(ctx,m,dark);
}
