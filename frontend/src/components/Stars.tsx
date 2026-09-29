import { useId } from 'react';

function StarShape({ fill, size }: { fill: number; size: number }) {
  // fill: 0..1, drawn as a clipped overlay so half stars read correctly
  const id = useId();
  const path = 'M12 2.5l2.9 6.1 6.6.8-4.9 4.6 1.3 6.6L12 17.3l-5.9 3.3 1.3-6.6-4.9-4.6 6.6-.8z';
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <defs>
        <clipPath id={id}>
          <rect x="0" y="0" width={24 * fill} height="24" />
        </clipPath>
      </defs>
      <path d={path} fill="none" stroke="#141414" strokeWidth="1.5" strokeLinejoin="round" />
      <path d={path} fill="#f7c948" stroke="#141414" strokeWidth="1.5" strokeLinejoin="round" clipPath={`url(#${id})`} />
    </svg>
  );
}

export function Stars({ value, size = 16 }: { value: number; size?: number }) {
  return (
    <span className="stars" role="img" aria-label={`${value.toFixed(1)} out of 5`}>
      {[0, 1, 2, 3, 4].map((i) => (
        <StarShape key={i} size={size} fill={Math.max(0, Math.min(1, value - i))} />
      ))}
    </span>
  );
}

/** A 1-5 picker built from real radio inputs (keyboard: arrow keys). */
export function StarInput({ value, onChange, name }: { value: number; onChange: (v: number) => void; name: string }) {
  const labels = ['Terrible', 'Poor', 'Okay', 'Good', 'Excellent'];
  return (
    <fieldset className="star-input">
      <legend className="visually-hidden">Your rating</legend>
      {[1, 2, 3, 4, 5].map((n) => (
        <label key={n} title={labels[n - 1]}>
          <input
            type="radio"
            name={name}
            value={n}
            checked={value === n}
            onChange={() => onChange(n)}
            aria-label={`${n} star${n > 1 ? 's' : ''}, ${labels[n - 1]}`}
          />
          <StarShape size={30} fill={n <= value ? 1 : 0} />
        </label>
      ))}
    </fieldset>
  );
}
