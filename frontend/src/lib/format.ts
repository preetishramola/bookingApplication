// Formatting and small date helpers. Prices from the API carry no currency; set it once here.
export const CURRENCY = '$';

export function money(amount: number): string {
  const rounded = Math.round(amount * 100) / 100;
  return `${CURRENCY}${rounded.toLocaleString('en-US', {
    minimumFractionDigits: Number.isInteger(rounded) ? 0 : 2,
    maximumFractionDigits: 2,
  })}`;
}

/** yyyy-mm-dd in local time (what the API expects). */
export function isoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function parseIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(iso: string, days: number): string {
  const date = parseIso(iso);
  date.setDate(date.getDate() + days);
  return isoDate(date);
}

export const today = () => isoDate(new Date());

export function nightsBetween(checkIn: string, checkOut: string): number {
  return Math.round((parseIso(checkOut).getTime() - parseIso(checkIn).getTime()) / 86_400_000);
}

export function niceDate(iso: string): string {
  return parseIso(iso).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
}

export function niceDateLong(iso: string): string {
  return parseIso(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function monthYear(yyyyMm: string): string {
  const [y, m] = yyyyMm.split('-').map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
}

export function place(h: { city?: string; state?: string; country?: string }): string {
  return [h.city, h.state ?? h.country].filter(Boolean).join(', ');
}

export const ROOM_LABEL: Record<string, string> = {
  STANDARD: 'Standard room',
  DELUXE: 'Deluxe room',
  SUITE: 'Suite',
};

export const ADD_ONS = [
  { id: 'BREAKFAST', label: 'Breakfast', price: 30, note: 'per stay' },
  { id: 'AIRPORT_PICKUP', label: 'Airport pickup', price: 80, note: 'one way' },
] as const;

/**
 * Mirrors the backend's pricing so guests see the total before paying:
 * WeekendPricingStrategy adds 15% to Saturday and Sunday nights; add-ons are flat per booking.
 * The server's figure on the confirmation is the one that counts.
 */
export function estimateTotal(pricePerNight: number, checkIn: string, checkOut: string, addOns: string[]) {
  let rooms = 0;
  let weekendNights = 0;
  const nights = nightsBetween(checkIn, checkOut);
  for (let i = 0; i < nights; i++) {
    const day = parseIso(addDays(checkIn, i)).getDay();
    const weekend = day === 0 || day === 6;
    if (weekend) weekendNights++;
    rooms += weekend ? pricePerNight * 1.15 : pricePerNight;
  }
  const extras = ADD_ONS.filter((a) => addOns.includes(a.id)).reduce((sum, a) => sum + a.price, 0);
  return { nights, weekendNights, rooms, extras, total: rooms + extras };
}
