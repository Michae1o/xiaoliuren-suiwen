import { motion, useTransform } from 'motion/react';
import dayURL from '../data/huiguan-materials/dew-mist-day.webp?inline';
import nightURL from '../data/huiguan-materials/dew-mist-night.webp?inline';

// One fixed condensation field with paired tonal responses. Draw it above
// the moving original atlas and below all record marks and foreground type.
// No drifting clouds, silhouettes, particles, sparkle or independent clock.
export default function HuiguanDewMist({ timeline,width,height }) {
  const night = useTransform(timeline, [0,.15,.89,1.1,1.87,2], [0,0,1,1,0,0]);
  const day = useTransform(night,n=>1-n);
  return <g data-hg-effect="dew-mist" aria-hidden="true" pointerEvents="none">
    <motion.image href={dayURL} width={width} height={height} preserveAspectRatio="none"
      style={{opacity:day}} data-hg-mist-tone="day"/>
    <motion.image href={nightURL} width={width} height={height} preserveAspectRatio="none"
      style={{opacity:night}} data-hg-mist-tone="night"/>
  </g>;
}
