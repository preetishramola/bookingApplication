import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import * as endpoints from '../api/endpoints';
import { errorMessage } from '../api/client';
import type { Booking, Review } from '../api/types';
import { Notice } from '../components/Notice';
import { StarInput, Stars } from '../components/Stars';
import { money, niceDate, nightsBetween, today } from '../lib/format';

export default function MyBookingsPage() {
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      const [b, r] = await Promise.all([endpoints.myBookings(), endpoints.myReviews()]);
      setBookings(b);
      setReviews(r);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  useEffect(() => {
    load();
  }, []);

  const now = today();
  const sorted = [...(bookings ?? [])].sort((a, b) => b.checkIn.localeCompare(a.checkIn));
  const upcoming = sorted.filter((b) => b.status === 'CONFIRMED' && b.checkIn > now).reverse();
  const stayed = sorted.filter((b) => b.status === 'CONFIRMED' && b.checkIn <= now);
  const cancelled = sorted.filter((b) => b.status === 'CANCELLED');
  const reviewFor = (bookingId: number) => reviews.find((r) => r.bookingId === bookingId);

  return (
    <div className="wrap">
      <h1 className="page-title">My bookings</h1>
      {error && <Notice kind="error">{error}</Notice>}
      {!bookings && !error && <div className="skeleton" style={{ height: 160, marginTop: 32 }} />}

      {bookings && bookings.length === 0 && (
        <div style={{ marginTop: 32 }}>
          <Notice>
            No bookings yet. <Link to="/">Find a stay</Link>
          </Notice>
        </div>
      )}

      {upcoming.length > 0 && (
        <Group title="Upcoming">
          {upcoming.map((b) => (
            <BookingRow key={b.bookingId} booking={b} onChanged={load} />
          ))}
        </Group>
      )}

      {stayed.length > 0 && (
        <Group title="Current and past stays">
          {stayed.map((b) => (
            <BookingRow key={b.bookingId} booking={b} review={reviewFor(b.bookingId)} canReview onChanged={load} />
          ))}
        </Group>
      )}

      {cancelled.length > 0 && (
        <Group title="Cancelled">
          {cancelled.map((b) => (
            <BookingRow key={b.bookingId} booking={b} onChanged={load} />
          ))}
        </Group>
      )}
    </div>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="booking-group">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

function BookingRow({
  booking,
  review,
  canReview = false,
  onChanged,
}: {
  booking: Booking;
  review?: Review;
  canReview?: boolean;
  onChanged: () => void;
}) {
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [writing, setWriting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nights = nightsBetween(booking.checkIn, booking.checkOut);
  const isCancelled = booking.status === 'CANCELLED';

  async function cancel() {
    setBusy(true);
    setError(null);
    try {
      await endpoints.cancelBooking(booking.bookingId);
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  async function removeReview() {
    if (!review) return;
    setBusy(true);
    try {
      await endpoints.deleteReview(review.id);
      onChanged();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="booking-row">
      <div>
        <h3>{booking.hotelName}</h3>
        <div className="facts">
          <span>
            {niceDate(booking.checkIn)} → {niceDate(booking.checkOut)} · {nights} night{nights > 1 ? 's' : ''}
          </span>
          <span>{money(booking.totalAmount)}</span>
          <span>Booking #{booking.bookingId}</span>
          {isCancelled && <span className="chip chip-muted">Cancelled</span>}
        </div>
      </div>

      <div className="actions">
        {canReview && !review && !writing && (
          <button type="button" className="btn btn-accent" onClick={() => setWriting(true)}>
            Leave a review
          </button>
        )}
        {!isCancelled &&
          booking.checkIn > today() &&
          (confirmCancel ? (
            <>
              <span className="hint">Cancel this booking?</span>
              <button type="button" className="btn btn-danger" onClick={cancel} disabled={busy}>
                Yes, cancel
              </button>
              <button type="button" className="btn btn-quiet" onClick={() => setConfirmCancel(false)}>
                Keep it
              </button>
            </>
          ) : (
            <button type="button" className="btn btn-quiet" onClick={() => setConfirmCancel(true)}>
              Cancel booking
            </button>
          ))}
      </div>

      {(error || writing || review) && (
        <div className="extra">
          {error && (
            <div style={{ marginBottom: 12 }}>
              <Notice kind="error">{error}</Notice>
            </div>
          )}
          {writing && (
            <ReviewForm
              bookingId={booking.bookingId}
              existing={review}
              onDone={() => {
                setWriting(false);
                onChanged();
              }}
              onCancel={() => setWriting(false)}
            />
          )}
          {review && !writing && (
            <div className="my-review">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Stars value={review.rating} />
                <strong>Your review</strong>
                {review.updatedAt && <span className="hint">Edited</span>}
              </div>
              {review.comment && <p style={{ whiteSpace: 'pre-line' }}>{review.comment}</p>}
              <div style={{ display: 'flex', gap: 16 }}>
                <button type="button" className="link-button" onClick={() => setWriting(true)}>
                  Edit
                </button>
                <button type="button" className="link-button" onClick={removeReview} disabled={busy}>
                  Delete
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </article>
  );
}

function ReviewForm({
  bookingId,
  existing,
  onDone,
  onCancel,
}: {
  bookingId: number;
  existing?: Review;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [rating, setRating] = useState(existing?.rating ?? 0);
  const [comment, setComment] = useState(existing?.comment ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (rating < 1) {
      setError('Pick a star rating first.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (existing) await endpoints.updateReview(existing.id, rating, comment.trim());
      else await endpoints.createReview(bookingId, rating, comment.trim());
      onDone();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <div className="review-box">
      <span className="label">{existing ? 'Edit your review' : 'How was your stay?'}</span>
      <StarInput name={`rating-${bookingId}`} value={rating} onChange={setRating} />
      <div className="field">
        <label htmlFor={`comment-${bookingId}`}>Tell other guests (optional)</label>
        <textarea
          id={`comment-${bookingId}`}
          className="textarea"
          maxLength={2000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="What stood out? The room, the breakfast, the location…"
        />
        <span className="hint">{2000 - comment.length} characters left</span>
      </div>
      {error && <Notice kind="error">{error}</Notice>}
      <div style={{ display: 'flex', gap: 8 }}>
        <button type="button" className="btn btn-dark" onClick={save} disabled={busy}>
          {busy ? 'Saving…' : existing ? 'Save changes' : 'Post review'}
        </button>
        <button type="button" className="btn btn-quiet" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
