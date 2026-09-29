import { Link } from 'react-router-dom';
import type { Hotel } from '../api/types';
import { Stars } from './Stars';
import { place } from '../lib/format';

interface Props {
  hotel: Hotel;
  featured?: boolean;
  badge?: string;
  distanceKm?: number;
  linkQuery?: string;
}

export function RatingLine({ hotel }: { hotel: Hotel }) {
  if (hotel.rating == null) return <span className="hint">Not rated yet</span>;
  return (
    <span className="rating-line">
      <Stars value={hotel.rating} />
      <strong>{hotel.rating.toFixed(1)}</strong>
      <span className="hint">
        {hotel.reviewCount > 0
          ? `${hotel.reviewCount} review${hotel.reviewCount === 1 ? '' : 's'}`
          : 'No guest reviews yet'}
      </span>
    </span>
  );
}

export default function HotelCard({ hotel, featured, badge, distanceKm, linkQuery = '' }: Props) {
  const href = `/hotels/${hotel.id}${linkQuery}`;

  return (
    <article className={`hotel-card${featured ? ' featured' : ''}`}>
      <div className="top">
        <span className="eyebrow">
          {place(hotel)}
          {distanceKm != null && ` · ${distanceKm < 1 ? '<1' : distanceKm.toFixed(1)} km away`}
        </span>
        {badge && <span className="chip chip-accent">{badge}</span>}
      </div>
      <h3>
        <Link to={href}>{hotel.name}</Link>
      </h3>
      {hotel.description && <p className="desc">{hotel.description}</p>}
      {hotel.amenities.length > 0 && (
        <div className="tags">
          {hotel.amenities.slice(0, 3).map((a) => (
            <span key={a} className="tag">
              {a}
            </span>
          ))}
          {hotel.amenities.length > 3 && <span className="tag">+{hotel.amenities.length - 3}</span>}
        </div>
      )}
      <div className="bottom">
        <RatingLine hotel={hotel} />
        <Link to={href} className={`btn ${featured ? 'btn-dark' : ''}`} aria-label={`View rooms at ${hotel.name}`}>
          View rooms
        </Link>
      </div>
    </article>
  );
}
