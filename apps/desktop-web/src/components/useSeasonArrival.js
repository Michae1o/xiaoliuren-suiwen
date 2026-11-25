import { useEffect } from 'react';
import { animate, useMotionValue } from 'motion/react';

export function useSeasonArrival(reduced, duration, ease = [.30,.12,.35,1]) {
  const value = useMotionValue(reduced ? 1 : 0);
  const [a,b,c,d] = ease;
  useEffect(() => {
    if (reduced) { value.jump(1); return; }
    value.jump(0);
    const animation = animate(value,1,{duration,ease:[a,b,c,d]});
    return () => animation.stop();
  }, [value,reduced,duration,a,b,c,d]);
  return value;
}
