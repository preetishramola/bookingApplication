import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as endpoints from '../api/endpoints';
import { ApiError, errorMessage } from '../api/client';
import type { Hotel } from '../api/types';
import HotelCard from '../components/HotelCard';
import { Notice } from '../components/Notice';
import { addDays, today } from '../lib/format';

type Mode = 'vibe' | 'city' | 'near';

interface Hit {
  hotel: Hotel;
  distanceKm?: number;
  matchScore?: number;
}

interface Results {
  hits: Hit[];
  title: string;
  ranked: 'match' | 'distance' | 'rating';
}

const EXAMPLES = ['Quiet and romantic, great breakfast', 'Family trip with a big pool', 'Work trip near the IT parks', 'Silent spa weekend in the hills'];

const MODES: { id: Mode; label: string }[] = [
  { id: 'vibe', label: 'By vibe' },
  { id: 'city', label: 'By city' },
  { id: 'near', label: 'Near me' },
];

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const mode = (params.get('mode') as Mode) || 'vibe';
  const q = params.get('q') ?? '';
  const city = params.get('city') ?? '';

  // Form state (committed to the URL on submit, so searches are shareable and back-button friendly)
  const [draftQ, setDraftQ] = useState(q);
  const [draftCity, setDraftCity] = useState(city);
  const [checkIn, setCheckIn] = useState(params.get('checkIn') ?? '');
  const [checkOut, setCheckOut] = useState(params.get('checkOut') ?? '');
  const [radius, setRadius] = useState(25);

  const [results, setResults] = useState<Results | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<{ message: string; vibeDown?: boolean } | null>(null);

  const [cityFilter, setCityFilter] = useState<string[]>([]);
  const [minRating, setMinRating] = useState(0);

  useEffect(() => setDraftQ(q), [q]);
  useEffect(() => setDraftCity(city), [city]);

  async function run(load: () => Promise<Results>) {
    setLoading(true);
    setError(null);
    setCityFilter([]);
    try {
      setResults(await load());
    } catch (e) {
      setResults(null);
      setError({ message: errorMessage(e), vibeDown: e instanceof ApiError && e.status === 503 });
    } finally {
      setLoading(false);
    }
  }

  // Load results whenever the committed search in the URL changes
  useEffect(() => {
    if (mode === 'near') return; // needs a location first, started from the button
    if (mode === 'vibe' && q) {
      run(async () => ({
        hits: await endpoints.vibeSearch(q),
        title: `Best matches for “${q}”`,
        ranked: 'match',
      }));
    } else if (mode === 'city' && city) {
      run(async () => ({
        hits: (await endpoints.searchHotels({ city })).map((hotel) => ({ hotel })),
        title: `Stays in ${city}`,
        ranked: 'rating',
      }));
    } else {
      run(async () => ({
        hits: (await endpoints.searchHotels({})).map((hotel) => ({ hotel })),
        title: 'All stays',
        ranked: 'rating',
      }));
    }
  }, [mode, q, city]);

  function commit(next: Record<string, string>) {
    const merged: Record<string, string> = { mode, ...next };
    if (checkIn) merged.checkIn = checkIn;
    if (checkOut) merged.checkOut = checkOut;
    setParams(Object.fromEntries(Object.entries(merged).filter(([, v]) => v)));
  }

  function findNearMe() {
    if (!('geolocation' in navigator)) {
      setError({ message: "Your browser can't share its location. Try searching by city." });
      return;
    }
    setLoading(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        run(async () => ({
          hits: await endpoints.nearbyHotels(pos.coords.latitude, pos.coords.longitude, radius),
          title: `Within ${radius} km of you`,
          ranked: 'distance',
        })),
      () => {
        setLoading(false);
        setError({ message: 'Location access was blocked. Allow it in your browser, or search by city.' });
      },
      { timeout: 10000 },
    );
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (checkIn && checkOut && checkOut <= checkIn) {
      setError({ message: 'Check-out must be after check-in.' });
      return;
    }
    if (mode === 'vibe') commit({ q: draftQ.trim() });
    else if (mode === 'city') commit({ city: draftCity.trim() });
    else {
      commit({});
      findNearMe();
    }
  }

  function switchMode(next: Mode) {
    setResults(null);
    setError(null);
    const keep: Record<string, string> = { mode: next };
    if (checkIn) keep.checkIn = checkIn;
    if (checkOut) keep.checkOut = checkOut;
    setParams(keep);
  }

  const cities = useMemo(
    () => [...new Set((results?.hits ?? []).map((h) => h.hotel.city).filter((c): c is string => !!c))].sort(),
    [results],
  );

  const visible = (results?.hits ?? []).filter(
    ({ hotel }) =>
      (cityFilter.length === 0 || (hotel.city && cityFilter.includes(hotel.city))) &&
      (minRating === 0 || (hotel.rating ?? 0) >= minRating),
  );

  const dateQuery =
    checkIn && checkOut && checkOut > checkIn ? `?checkIn=${checkIn}&checkOut=${checkOut}` : '';

  return (
    <>
      <section className="search-hero">
        <div className="wrap">
          <h1>Where to next?</h1>

          <div className="segmented" role="group" aria-label="Search mode">
            {MODES.map((m) => (
              <button key={m.id} type="button" aria-pressed={mode === m.id} onClick={() => switchMode(m.id)}>
                {m.label}
              </button>
            ))}
          </div>

          <form className="search-bar" onSubmit={onSubmit}>
            {mode === 'vibe' && (
              <div className="cell">
                <label htmlFor="q">Describe your stay</label>
                <input
                  id="q"
                  value={draftQ}
                  onChange={(e) => setDraftQ(e.target.value)}
                  placeholder="A quiet boutique hotel near a park, great breakfast"
                  required
                  maxLength={300}
                />
              </div>
            )}
            {mode === 'city' && (
              <div className="cell">
                <label htmlFor="city">City</label>
                <input
                  id="city"
                  value={draftCity}
                  onChange={(e) => setDraftCity(e.target.value)}
                  placeholder="Bangalore"
                  required
                />
              </div>
            )}
            {mode === 'near' && (
              <div className="cell near-cell">
                <div style={{ flex: 1 }}>
                  <label htmlFor="radius">Search radius</label>
                  <select id="radius" value={radius} onChange={(e) => setRadius(Number(e.target.value))}>
                    {[5, 10, 25, 50, 100].map((r) => (
                      <option key={r} value={r}>
                        Within {r} km
                      </option>
                    ))}
                  </select>
                </div>
                <span className="hint">Uses your browser location once. It isn't stored.</span>
              </div>
            )}
            <div className="cell">
              <label htmlFor="checkIn">Check-in</label>
              <input
                id="checkIn"
                type="date"
                min={today()}
                value={checkIn}
                onChange={(e) => {
                  setCheckIn(e.target.value);
                  if (e.target.value && (!checkOut || checkOut <= e.target.value)) setCheckOut(addDays(e.target.value, 1));
                }}
              />
            </div>
            <div className="cell">
              <label htmlFor="checkOut">Check-out</label>
              <input
                id="checkOut"
                type="date"
                min={checkIn ? addDays(checkIn, 1) : today()}
                value={checkOut}
                onChange={(e) => setCheckOut(e.target.value)}
              />
            </div>
            <button type="submit" className="btn btn-accent">
              {mode === 'near' ? 'Find nearby' : 'Search'}
            </button>
          </form>

          {mode === 'vibe' && (
            <div className="examples">
              <span>Try:</span>
              {EXAMPLES.map((ex) => (
                <button
                  key={ex}
                  type="button"
                  onClick={() => {
                    setDraftQ(ex);
                    commit({ q: ex });
                  }}
                >
                  {ex}
                </button>
              ))}
            </div>
          )}
        </div>
      </section>

      <div className="wrap results-layout">
        <aside className="filters" aria-label="Filters">
          {cities.length > 1 && (
            <fieldset>
              <legend>City</legend>
              {cities.map((c) => (
                <label key={c} className="check">
                  <input
                    type="checkbox"
                    checked={cityFilter.includes(c)}
                    onChange={(e) =>
                      setCityFilter((prev) => (e.target.checked ? [...prev, c] : prev.filter((x) => x !== c)))
                    }
                  />
                  {c}
                </label>
              ))}
            </fieldset>
          )}
          <fieldset>
            <legend>Guest rating</legend>
            {[
              [0, 'Any'],
              [4, '4.0 and up'],
              [4.5, '4.5 and up'],
            ].map(([value, label]) => (
              <label key={value} className="check">
                <input
                  type="radio"
                  name="minRating"
                  checked={minRating === value}
                  onChange={() => setMinRating(value as number)}
                />
                {label}
              </label>
            ))}
          </fieldset>
          <p className="explainer">
            Vibe search ranks hotels by meaning, so “somewhere lively for kids” finds the pool and the kids club even if
            you never type those words.
          </p>
        </aside>

        <section aria-live="polite" aria-busy={loading}>
          {error && (
            <div style={{ marginBottom: 20 }}>
              <Notice kind="error">
                {error.vibeDown ? (
                  <>
                    Vibe search is unavailable right now.{' '}
                    <button type="button" className="link-button" onClick={() => switchMode('city')}>
                      Search by city instead
                    </button>
                  </>
                ) : (
                  error.message
                )}
              </Notice>
            </div>
          )}

          {mode === 'near' && !results && !loading && !error && (
            <Notice>Pick a radius and press “Find nearby” to see stays around you.</Notice>
          )}

          {loading && (
            <div className="results-grid" aria-label="Loading">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="skeleton" style={{ height: 290 }} />
              ))}
            </div>
          )}

          {!loading && results && (
            <>
              <div className="results-head">
                <h2>{results.title}</h2>
                <span className="hint">
                  {visible.length} of {results.hits.length} ·{' '}
                  {results.ranked === 'match'
                    ? 'best match first'
                    : results.ranked === 'distance'
                      ? 'nearest first'
                      : 'highest rated first'}
                </span>
              </div>
              {visible.length === 0 ? (
                <Notice>
                  {results.hits.length === 0
                    ? 'No stays found. Try other words, a bigger radius, or another city.'
                    : 'No stays match these filters.'}
                </Notice>
              ) : (
                <div className="results-grid">
                  {visible.map((hit, i) => (
                    <HotelCard
                      key={hit.hotel.id}
                      hotel={hit.hotel}
                      distanceKm={hit.distanceKm}
                      featured={results.ranked === 'match' && i === 0}
                      badge={results.ranked === 'match' && i === 0 ? 'Best match' : undefined}
                      linkQuery={dateQuery}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </>
  );
}
