// Identical edge attenuation for the CSS and SVG decorative surfaces.
const stops = [[0, 0], [0.002, 0], [0.017, 0.11], [0.036, 0.39], [0.059, 0.78], [0.088, 1], [0.912, 1], [0.941, 0.78], [0.964, 0.39], [0.983, 0.11], [0.998, 0], [1, 0]];
export const edgeMaskImage = 'linear-gradient(to bottom, ' +
  stops.map(([offset,alpha]) => `rgb(0 0 0 / ${alpha}) ${offset*100}%`).join(', ') + ')';
export const atmosphereEdges = { maskImage:edgeMaskImage, WebkitMaskImage:edgeMaskImage, maskRepeat:'no-repeat' };

export function AtmosphereEdgeMask({id,width,height}) {
  return <>
    <linearGradient id={id+'-gradient'} gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2={height}>
      {stops.map(([offset,alpha]) => <stop key={offset} offset={offset} stopColor="white" stopOpacity={alpha}/>)}
    </linearGradient>
    <mask id={id} maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse"
          x="0" y="0" width={width} height={height} style={{maskType:'alpha'}}>
      <rect x="0" y="0" width={width} height={height} fill={`url(#${id}-gradient)`}/>
    </mask>
  </>;
}
