import { motion, useTransform } from 'motion/react';
import { useSeasonArrival } from './useSeasonArrival.js';
import style from '../styles/huiguan-spring-balance.css?inline';

const cap = v => Math.max(0, Math.min(1, v));
export default function SpringBalance({ timeline, reduced }) {
  const t = useSeasonArrival(reduced, 1.82, [.22,.66,.29,1]);
  const top = useTransform(t, [0, 1], ['-126%', '-13%']);
  const bottom = useTransform(t, [0, 1], ['126%', '13%']);
  const opacity = useTransform(() => {
    const center = cap(1 - Math.abs(timeline.get() - 1));
    return (.42 + .28 * t.get()) * (.85 + .15 * center);
  });
  return <div className="hg-spring-effect" aria-hidden="true"><style>{style}</style><motion.div className="hg-spring-balance" style={{opacity}}>
    <motion.div className="hg-spring-band hg-spring-band-top" style={{y:top}} />
    <motion.div className="hg-spring-band hg-spring-band-bottom" style={{y:bottom}} />
  </motion.div></div>;
}

