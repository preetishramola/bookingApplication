import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import * as endpoints from '../api/endpoints';
import { ApiError, errorMessage } from '../api/client';
import type { Hotel, RoomType } from '../api/types';
import { Notice } from '../components/Notice';

export default function AdminPage() {
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const reloadHotels = () => endpoints.searchHotels({}).then(setHotels).catch(() => undefined);
  useEffect(() => {
    reloadHotels();
  }, []);

  return (
    <div className="wrap">
      <h1 className="page-title">Admin</h1>
      <p className="hint">Add hotels and rooms. New hotels are embedded for vibe search automatically.</p>
      <div className="admin-grid">
        <AddHotelForm onAdded={reloadHotels} />
        <div className="stack">
          <AddRoomForm hotels={hotels} />
          <EmbeddingsPanel />
        </div>
      </div>
    </div>
  );
}

const optionalNumber = (v: string) => (v.trim() === '' ? undefined : Number(v));

function AddHotelForm({ onAdded }: { onAdded: () => void }) {
  const empty = { name: '', city: '', address: '', state: '', country: '', rating: '', description: '', latitude: '', longitude: '', amenities: '' };
  const [form, setForm] = useState(empty);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [added, setAdded] = useState<Hotel | null>(null);

  const set = (key: keyof typeof empty) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    setAdded(null);
    try {
      const hotel = await endpoints.addHotel({
        name: form.name.trim(),
        city: form.city.trim(),
        address: form.address.trim() || undefined,
        state: form.state.trim() || undefined,
        country: form.country.trim() || undefined,
        rating: optionalNumber(form.rating),
        description: form.description.trim() || undefined,
        latitude: optionalNumber(form.latitude),
        longitude: optionalNumber(form.longitude),
        amenities: form.amenities
          .split(',')
          .map((a) => a.trim())
          .filter(Boolean),
      });
      setAdded(hotel);
      setForm(empty);
      onAdded();
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) setFieldErrors(err.fieldErrors);
      else setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const field = (key: keyof typeof empty, label: string, props: Record<string, unknown> = {}, full = false) => (
    <div className={`field${full ? ' full' : ''}`}>
      <label htmlFor={`h-${key}`}>{label}</label>
      <input id={`h-${key}`} className="input" value={form[key]} onChange={set(key)} {...props} />
      {fieldErrors[key] && <span className="field-error">{fieldErrors[key]}</span>}
    </div>
  );

  return (
    <form className="panel" onSubmit={onSubmit}>
      <h2>Add a hotel</h2>
      {added && (
        <Notice kind="success">
          Added <Link to={`/hotels/${added.id}`}>{added.name}</Link> (id {added.id}). Add rooms to it on the right.
        </Notice>
      )}
      {error && <Notice kind="error">{error}</Notice>}
      <div className="form-grid">
        {field('name', 'Name *', { required: true }, true)}
        {field('city', 'City *', { required: true })}
        {field('state', 'State')}
        {field('country', 'Country')}
        {field('rating', 'Initial rating', { type: 'number', min: 0, max: 5, step: 0.1 })}
        {field('address', 'Address', {}, true)}
        {field('latitude', 'Latitude', { type: 'number', step: 'any', min: -90, max: 90 })}
        {field('longitude', 'Longitude', { type: 'number', step: 'any', min: -180, max: 180 })}
        <div className="field full">
          <label htmlFor="h-description">Description</label>
          <textarea id="h-description" className="textarea" maxLength={2000} value={form.description} onChange={set('description')} />
          <span className="hint">Vibe search reads this, so describe the feel of the place.</span>
        </div>
        {field('amenities', 'Amenities (comma separated)', { placeholder: 'WiFi, Pool, Spa' }, true)}
      </div>
      <button type="submit" className="btn btn-dark" disabled={busy} style={{ alignSelf: 'flex-start' }}>
        {busy ? 'Adding…' : 'Add hotel'}
      </button>
    </form>
  );
}

function AddRoomForm({ hotels }: { hotels: Hotel[] }) {
  const [hotelId, setHotelId] = useState('');
  const [roomNumber, setRoomNumber] = useState('');
  const [type, setType] = useState<RoomType>('STANDARD');
  const [price, setPrice] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      const room = await endpoints.addRoom(Number(hotelId), { roomNumber: roomNumber.trim(), type, pricePerNight: Number(price) });
      setMessage({ kind: 'success', text: `Room ${room.roomNumber} added.` });
      setRoomNumber('');
    } catch (err) {
      setMessage({ kind: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="panel" onSubmit={onSubmit}>
      <h2>Add a room</h2>
      {message && <Notice kind={message.kind}>{message.text}</Notice>}
      <div className="field">
        <label htmlFor="r-hotel">Hotel</label>
        <select id="r-hotel" className="select" required value={hotelId} onChange={(e) => setHotelId(e.target.value)}>
          <option value="">Choose a hotel</option>
          {hotels.map((h) => (
            <option key={h.id} value={h.id}>
              {h.name} · {h.city}
            </option>
          ))}
        </select>
      </div>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="r-number">Room number</label>
          <input id="r-number" className="input" required value={roomNumber} onChange={(e) => setRoomNumber(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="r-type">Type</label>
          <select id="r-type" className="select" value={type} onChange={(e) => setType(e.target.value as RoomType)}>
            <option value="STANDARD">Standard</option>
            <option value="DELUXE">Deluxe</option>
            <option value="SUITE">Suite</option>
          </select>
        </div>
        <div className="field full">
          <label htmlFor="r-price">Price per night</label>
          <input id="r-price" className="input" type="number" min={1} step="0.01" required value={price} onChange={(e) => setPrice(e.target.value)} />
        </div>
      </div>
      <button type="submit" className="btn btn-dark" disabled={busy} style={{ alignSelf: 'flex-start' }}>
        {busy ? 'Adding…' : 'Add room'}
      </button>
    </form>
  );
}

function EmbeddingsPanel() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: 'error' | 'success'; text: string } | null>(null);

  async function run(onlyMissing: boolean) {
    setBusy(true);
    setMessage(null);
    try {
      const r = await endpoints.refreshEmbeddings(onlyMissing);
      setMessage({
        kind: r.failed > 0 ? 'error' : 'success',
        text:
          r.failed > 0
            ? `Embedded ${r.embedded}, then the model became unreachable; ${r.failed + r.remaining} still missing.`
            : `Embedded ${r.embedded} hotel${r.embedded === 1 ? '' : 's'}.`,
      });
    } catch (err) {
      setMessage({ kind: 'error', text: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel">
      <h2>Vibe search index</h2>
      <p className="hint">Hotels added while Bedrock was unreachable have no embedding and don’t appear in vibe search.</p>
      {message && <Notice kind={message.kind}>{message.text}</Notice>}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <button type="button" className="btn btn-dark" onClick={() => run(true)} disabled={busy}>
          Embed missing
        </button>
        <button type="button" className="btn btn-quiet" onClick={() => run(false)} disabled={busy}>
          Rebuild all
        </button>
      </div>
    </section>
  );
}
