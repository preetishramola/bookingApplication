import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import * as endpoints from '../api/endpoints';
import { ApiError, errorMessage } from '../api/client';
import type { AddOn, Booking, Hotel, Page, PaymentMethod, Review, Room } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { RatingLine } from '../components/HotelCard';
import { Notice } from '../components/Notice';
import { Stars } from '../components/Stars';
import {
  ADD_ONS,
  ROOM_LABEL,
  addDays,
  estimateTotal,
  money,
  monthYear,
  niceDate,
  niceDateLong,
  today,
} from '../lib/format';

export default function HotelPage() {
  const { id } = useParams();
  const hotelId = Number(id);
  const [hotel, setHotel] = useState<Hotel | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setHotel(null);
    setError(null);
    endpoints
      .getHotel(hotelId)
      .then(setHotel)
      .catch((e) => setError(e instanceof ApiError && e.status === 404 ? 'This hotel does not exist.' : errorMessage(e)));
  }, [hotelId]);

  if (error) {
    return (
      <div className="wrap">
        <Link to="/" className="back-link">
          ← All stays
        </Link>
        <Notice kind="error">{error}</Notice>
      </div>
    );
  }

  if (!hotel) {
    return (
      <div className="wrap" aria-busy="true">
        <div className="skeleton" style={{ height: 40, width: 120, margin: '28px 0 20px' }} />
        <div className="skeleton" style={{ height: 64, width: '60%' }} />
      </div>
    );
  }

  return (
    <div className="wrap">
      <Link to="/" className="back-link">
        ← All stays
      </Link>
      <div className="hotel-layout">
        <div>
          <span className="eyebrow">{[hotel.city, hotel.state, hotel.country].filter(Boolean).join(' · ')}</span>
          <h1 className="hotel-title">{hotel.name}</h1>
          <div className="hotel-meta">
            <RatingLine hotel={hotel} />
            {hotel.address && <span>{hotel.address}</span>}
            <a href="#book" className="btn btn-accent mobile-only">
              Check availability
            </a>
            {hotel.latitude != null && hotel.longitude != null && (
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${hotel.latitude},${hotel.longitude}`}
                target="_blank"
                rel="noreferrer"
              >
                Open in Maps
              </a>
            )}
          </div>

          {hotel.description && (
            <section className="section">
              <h2 className="visually-hidden">About</h2>
              <p className="lead">{hotel.description}</p>
            </section>
          )}

          {hotel.amenities.length > 0 && (
            <section className="section">
              <h2>What’s here</h2>
              <ul className="amenity-list">
                {hotel.amenities.map((a) => (
                  <li key={a}>{a}</li>
                ))}
              </ul>
            </section>
          )}

          <ReviewsSection hotel={hotel} />
        </div>

        <BookingPanel hotel={hotel} />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function BookingPanel({ hotel }: { hotel: Hotel }) {
  const { user } = useAuth();
  const location = useLocation();
  const [params, setParams] = useSearchParams();

  const initialIn = params.get('checkIn') ?? '';
  const initialOut = params.get('checkOut') ?? '';
  const [checkIn, setCheckIn] = useState(initialIn);
  const [checkOut, setCheckOut] = useState(initialOut);
  const datesValid = !!checkIn && !!checkOut && checkOut > checkIn && checkIn >= today();

  const [rooms, setRooms] = useState<Room[] | null>(null);
  const [roomsError, setRoomsError] = useState<string | null>(null);
  const [roomId, setRoomId] = useState<number | null>(null);
  const [addOns, setAddOns] = useState<AddOn[]>([]);
  const [payment, setPayment] = useState<PaymentMethod>('UPI');

  const [submitting, setSubmitting] = useState(false);
  const [bookError, setBookError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState<Booking | null>(null);

  // One key per booking attempt: a retry after a network error reuses it, so the server
  // can't create the booking twice. It resets when the guest changes what they're booking.
  const idempotencyKey = useRef<string>(crypto.randomUUID());
  useEffect(() => {
    idempotencyKey.current = crypto.randomUUID();
  }, [roomId, checkIn, checkOut, addOns, payment]);

  useEffect(() => {
    setRoomsError(null);
    setRooms(null);
    const load = datesValid
      ? endpoints.getAvailableRooms(hotel.id, checkIn, checkOut)
      : endpoints.getRooms(hotel.id);
    load
      .then((r) => {
        const sorted = [...r].sort((a, b) => a.pricePerNight - b.pricePerNight);
        setRooms(sorted);
        setRoomId((current) => (sorted.some((x) => x.id === current) ? current : (sorted[0]?.id ?? null)));
      })
      .catch((e) => setRoomsError(errorMessage(e)));
    if (datesValid) setParams({ checkIn, checkOut }, { replace: true });
  }, [hotel.id, checkIn, checkOut, datesValid]);

  const room = rooms?.find((r) => r.id === roomId) ?? null;
  const estimate = room && datesValid ? estimateTotal(room.pricePerNight, checkIn, checkOut, addOns) : null;

  async function book() {
    if (!room || !datesValid) return;
    setSubmitting(true);
    setBookError(null);
    try {
      const booking = await endpoints.createBooking(
        { roomId: room.id, checkIn, checkOut, addOns, paymentMethod: payment },
        idempotencyKey.current,
      );
      setConfirmed(booking);
      idempotencyKey.current = crypto.randomUUID();
    } catch (e) {
      setBookError(errorMessage(e));
      if (e instanceof ApiError && e.status === 409) {
        // Someone else got the room first: refresh what's still free
        endpoints.getAvailableRooms(hotel.id, checkIn, checkOut).then(setRooms).catch(() => undefined);
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (confirmed) {
    return (
      <aside className="booking-panel" aria-label="Booking">
        <span className="chip chip-accent" style={{ alignSelf: 'flex-start' }}>
          Confirmed
        </span>
        <h2>You’re booked at {confirmed.hotelName}</h2>
        <div className="price-breakdown" style={{ borderTop: 0, paddingTop: 0 }}>
          <div className="row">
            <span>Booking</span>
            <span>#{confirmed.bookingId}</span>
          </div>
          <div className="row">
            <span>Dates</span>
            <span>
              {niceDate(confirmed.checkIn)} → {niceDate(confirmed.checkOut)}
            </span>
          </div>
          <div className="row total">
            <span>Paid</span>
            <span>{money(confirmed.totalAmount)}</span>
          </div>
        </div>
        <Link to="/bookings" className="btn btn-dark btn-block">
          Go to my bookings
        </Link>
        <button type="button" className="link-button" onClick={() => setConfirmed(null)}>
          Book another room
        </button>
      </aside>
    );
  }

  return (
    <aside className="booking-panel" aria-label="Booking" id="book">
      <h2>Book a room</h2>

      <div className="two-col">
        <div className="field">
          <label htmlFor="bp-in">Check-in</label>
          <input
            id="bp-in"
            className="input"
            type="date"
            min={today()}
            value={checkIn}
            onChange={(e) => {
              setCheckIn(e.target.value);
              if (e.target.value && (!checkOut || checkOut <= e.target.value)) setCheckOut(addDays(e.target.value, 1));
            }}
          />
        </div>
        <div className="field">
          <label htmlFor="bp-out">Check-out</label>
          <input
            id="bp-out"
            className="input"
            type="date"
            min={checkIn ? addDays(checkIn, 1) : today()}
            value={checkOut}
            onChange={(e) => setCheckOut(e.target.value)}
          />
        </div>
      </div>

      {roomsError && <Notice kind="error">{roomsError}</Notice>}
      {!rooms && !roomsError && <div className="skeleton" style={{ height: 120 }} />}

      {rooms && (
        <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
          <legend className="label" style={{ marginBottom: 10 }}>
            {datesValid ? 'Available rooms' : 'Rooms — pick dates to check availability'}
          </legend>
          {rooms.length === 0 ? (
            <Notice>No rooms are free for these dates. Try other dates.</Notice>
          ) : (
            <div className="room-options">
              {rooms.map((r) => (
                <label key={r.id} className="room-option">
                  <input type="radio" name="room" checked={roomId === r.id} onChange={() => setRoomId(r.id)} />
                  <span>
                    <span className="name">{ROOM_LABEL[r.type] ?? r.type}</span>
                    <span className="hint"> · Room {r.roomNumber}</span>
                  </span>
                  <span className="price">
                    {money(r.pricePerNight)}
                    <small>per night</small>
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>
      )}

      {rooms && rooms.length > 0 && (
        <>
          <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
            <legend className="label" style={{ marginBottom: 10 }}>
              Extras
            </legend>
            {ADD_ONS.map((a) => (
              <label key={a.id} className="check">
                <input
                  type="checkbox"
                  checked={addOns.includes(a.id)}
                  onChange={(e) =>
                    setAddOns((prev) => (e.target.checked ? [...prev, a.id] : prev.filter((x) => x !== a.id)))
                  }
                />
                {a.label}
                <span className="hint" style={{ marginLeft: 'auto' }}>
                  +{money(a.price)} {a.note}
                </span>
              </label>
            ))}
          </fieldset>

          <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className="label" style={{ marginBottom: 10 }}>
              Pay with
            </legend>
            <div className="pay-options">
              <label>
                <input type="radio" name="pay" checked={payment === 'UPI'} onChange={() => setPayment('UPI')} />
                UPI
              </label>
              <label>
                <input
                  type="radio"
                  name="pay"
                  checked={payment === 'CREDIT_CARD'}
                  onChange={() => setPayment('CREDIT_CARD')}
                />
                Credit card
              </label>
            </div>
          </fieldset>

          {estimate && (
            <div className="price-breakdown">
              <div className="row">
                <span>
                  {estimate.nights} night{estimate.nights > 1 ? 's' : ''}
                  {estimate.weekendNights > 0 && (
                    <span className="hint"> · incl. {estimate.weekendNights} weekend (+15%)</span>
                  )}
                </span>
                <span>{money(estimate.rooms)}</span>
              </div>
              {estimate.extras > 0 && (
                <div className="row">
                  <span>Extras</span>
                  <span>{money(estimate.extras)}</span>
                </div>
              )}
              <div className="row total">
                <span>Total</span>
                <span>{money(estimate.total)}</span>
              </div>
            </div>
          )}

          {bookError && <Notice kind="error">{bookError}</Notice>}

          {user ? (
            <button
              type="button"
              className="btn btn-accent btn-block"
              style={{ minHeight: 52, fontSize: 17 }}
              disabled={!room || !datesValid || submitting}
              onClick={book}
            >
              {submitting ? 'Booking…' : datesValid ? `Book and pay${estimate ? ` ${money(estimate.total)}` : ''}` : 'Pick your dates'}
            </button>
          ) : (
            <Link
              to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
              className="btn btn-dark btn-block"
              style={{ minHeight: 52 }}
            >
              Sign in to book
            </Link>
          )}
          <p className="hint">Payments are simulated in this demo; no money moves.</p>
        </>
      )}
    </aside>
  );
}

// ---------------------------------------------------------------------------

const PAGE_SIZE = 5;

function ReviewsSection({ hotel }: { hotel: Hotel }) {
  const [page, setPage] = useState<Page<Review> | null>(null);
  const [items, setItems] = useState<Review[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    setItems([]);
    endpoints
      .hotelReviews(hotel.id, 0, PAGE_SIZE)
      .then((p) => {
        setPage(p);
        setItems(p.items);
      })
      .catch((e) => setError(errorMessage(e)));
  }, [hotel.id]);

  async function loadMore() {
    if (!page) return;
    setLoadingMore(true);
    try {
      const next = await endpoints.hotelReviews(hotel.id, page.page + 1, PAGE_SIZE);
      setPage(next);
      setItems((prev) => [...prev, ...next.items]);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section className="section" style={{ borderBottom: 0 }} aria-labelledby="reviews-title">
      <h2 id="reviews-title">Guest reviews</h2>

      {hotel.reviewCount > 0 && hotel.rating != null ? (
        <div className="review-summary">
          <span className="big">{hotel.rating.toFixed(1)}</span>
          <div>
            <Stars value={hotel.rating} size={20} />
            <div className="hint">
              from {hotel.reviewCount} verified stay{hotel.reviewCount === 1 ? '' : 's'}
            </div>
          </div>
        </div>
      ) : (
        <p className="hint" style={{ marginBottom: 16 }}>
          No guest reviews yet. Reviews come only from guests who booked and stayed here.
        </p>
      )}

      {error && <Notice kind="error">{error}</Notice>}

      {items.length > 0 && (
        <ul className="review-list">
          {items.map((r) => (
            <li key={r.id} className="review">
              <div className="head">
                <Stars value={r.rating} />
                <strong>{r.reviewerName}</strong>
                <span>Stayed {monthYear(r.stayMonth)}</span>
                <span className="chip chip-muted">Verified stay</span>
                {r.updatedAt && <span>Edited</span>}
              </div>
              {r.comment && <p className="body">{r.comment}</p>}
              <span className="hint">Written {niceDateLong(r.createdAt.slice(0, 10))}</span>
            </li>
          ))}
        </ul>
      )}

      {page && page.page + 1 < page.totalPages && (
        <button type="button" className="btn btn-quiet" onClick={loadMore} disabled={loadingMore} style={{ marginTop: 16 }}>
          {loadingMore ? 'Loading…' : `Show more reviews (${page.totalItems - items.length} left)`}
        </button>
      )}
    </section>
  );
}
