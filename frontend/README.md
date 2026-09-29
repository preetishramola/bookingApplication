# stayline — frontend

React + TypeScript (Vite) UI for the Hotel Booking API. Plain CSS, no UI kit; white, black and one yellow accent.

## Pages

| Route | What it does |
|---|---|
| `/` | Search **by vibe** (semantic), **by city**, or **near me** (browser location), with dates, city and rating filters |
| `/hotels/:id` | Hotel details, amenities, live room availability for your dates, extras, UPI/card, price breakdown, booking; verified guest reviews |
| `/login`, `/register` | JWT sign-in / sign-up |
| `/bookings` | Upcoming, past and cancelled bookings; cancel upcoming ones; leave, edit or delete a review for stays that have started |
| `/admin` | Admins only: add hotels and rooms, rebuild the vibe-search index |

## Run it locally

```bash
# 1. Backend (from the repo root). The default rate limit (10 req/min per IP) is too low for clicking around a UI.
docker compose up -d
RATE_LIMIT_PER_MINUTE=300 ./mvnw spring-boot:run

# 2. Frontend
cd frontend
npm install
npm run dev          # http://localhost:5173
```

The dev server proxies `/api` to `http://localhost:8080`, so there is no CORS setup. Point it elsewhere with
`VITE_API_TARGET=http://host:port npm run dev`.

Demo login: `alice@example.com` / `password123` (admin: `admin@example.com` / `admin123`, local dev only).

## Build

```bash
npm run build        # type-checks, then writes static files to dist/
```

`dist/` is plain static files. In production serve them from the same domain as the API (for example
Caddy: files for `/`, `reverse_proxy` for `/api/*`) so the browser never makes cross-origin calls.

## Notes

- Prices carry no currency in the API; the symbol is set once in `src/lib/format.ts` (`CURRENCY`).
- The price shown before booking mirrors the backend rules (+15% on Saturday/Sunday nights, breakfast +30,
  airport pickup +80). The server's total on the confirmation is the one charged.
- Each booking attempt sends an `Idempotency-Key`; a retry after a network error reuses it, so a double click
  or flaky connection can't book twice.
- The JWT is kept in `localStorage` and decoded only to show your name and the admin link. The server checks
  every request.
