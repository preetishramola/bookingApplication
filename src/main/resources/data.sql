-- Ensure users table has expected columns (add if missing) with safe defaults
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255) DEFAULT '' NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(50) DEFAULT 'ROLE_USER' NOT NULL;

-- Seed data for Hotel Booking demo application.
-- Embeddings are NOT seeded here: the app generates them on startup (app.vibe-search.backfill-on-startup).
INSERT INTO hotels (id, name, address, city, state, country, rating, latitude, longitude, description) VALUES
  (1, 'Sunset Inn', '10 Main Street', 'New York', 'NY', 'USA', 4.5, 40.7128, -74.0060,
   'A classic, no-frills city hotel in downtown Manhattan. Practical rooms, fast WiFi and a 24-hour front desk, a short walk from the subway. Popular with business travellers.'),
  (2, 'Harbor View', '22 Bay Road', 'Boston', 'MA', 'USA', 4.7, 42.3601, -71.0589,
   'Waterfront hotel with sweeping harbor views, a seafood restaurant and sunset rooftop bar. Great for weekend getaways by the sea.'),
  (3, 'The Cubbon Courtyard', '4 Kasturba Road', 'Bangalore', 'Karnataka', 'India', 4.8, 12.9745, 77.5960,
   'An intimate, quiet boutique hotel with only 14 rooms, tucked beside Cubbon Park. Candle-lit courtyard dinners, rooftop garden and a much-loved slow breakfast with fresh South Indian dishes. Perfect for couples and romantic escapes.'),
  (4, 'Whitefield Tech Suites', 'ITPL Main Road, Whitefield', 'Bangalore', 'Karnataka', 'India', 4.2, 12.9698, 77.7500,
   'Modern business hotel next to the IT parks. Meeting rooms, co-working lounge, express check-in and airport shuttle. Efficient rather than charming.'),
  (5, 'MG Road Grand', '120 MG Road', 'Bangalore', 'Karnataka', 'India', 4.4, 12.9756, 77.6069,
   'A lively, large family-friendly hotel on MG Road with a kids club, big pool and buffet restaurant. Walking distance to shopping, pubs and nightlife.'),
  (6, 'Nandi Hills Retreat', 'Nandi Hills Road', 'Chikkaballapur', 'Karnataka', 'India', 4.6, 13.3702, 77.6835,
   'A serene hilltop spa retreat an hour outside Bangalore. Yoga at sunrise, Ayurvedic spa, farm-to-table breakfast and total silence. Ideal for a peaceful detox weekend.')
ON CONFLICT (id) DO NOTHING;

INSERT INTO hotel_amenities (hotel_id, amenity)
SELECT v.hotel_id, v.amenity
FROM (VALUES
  (1, 'WiFi'), (1, '24-hour front desk'), (1, 'Business center'),
  (2, 'WiFi'), (2, 'Seafood restaurant'), (2, 'Rooftop bar'), (2, 'Harbor view'),
  (3, 'WiFi'), (3, 'Gourmet breakfast'), (3, 'Rooftop garden'), (3, 'Courtyard dining'), (3, 'Couples spa'),
  (4, 'WiFi'), (4, 'Meeting rooms'), (4, 'Co-working lounge'), (4, 'Airport shuttle'), (4, 'Gym'),
  (5, 'WiFi'), (5, 'Swimming pool'), (5, 'Kids club'), (5, 'Buffet restaurant'), (5, 'Bar'),
  (6, 'Spa'), (6, 'Yoga'), (6, 'Farm-to-table breakfast'), (6, 'Hiking trails')
) AS v(hotel_id, amenity)
WHERE NOT EXISTS (SELECT 1 FROM hotel_amenities a WHERE a.hotel_id = v.hotel_id);

INSERT INTO rooms (id, hotel_id, room_number, type, price_per_night, status) VALUES
  (1, 1, '101', 'STANDARD', 120.00, 'AVAILABLE'),
  (2, 1, '102', 'DELUXE', 180.00, 'AVAILABLE'),
  (3, 2, '201', 'SUITE', 260.00, 'AVAILABLE'),
  (4, 3, 'C1', 'DELUXE', 150.00, 'AVAILABLE'),
  (5, 3, 'C2', 'SUITE', 220.00, 'AVAILABLE'),
  (6, 4, '301', 'STANDARD', 90.00, 'AVAILABLE'),
  (7, 5, '501', 'STANDARD', 110.00, 'AVAILABLE'),
  (8, 5, '502', 'DELUXE', 160.00, 'AVAILABLE'),
  (9, 6, 'V1', 'SUITE', 240.00, 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- Demo logins (local dev only): alice@example.com / bob@example.com -> password123, admin@example.com -> admin123
INSERT INTO users (id, name, email, password, role) VALUES
  (1, 'Alice Johnson', 'alice@example.com', '$2y$10$6ZjdZZiQ1KFxMcwQBRqlNuER7CBm5jRHHs6rKe..VaL3g1IQiwaIO', 'ROLE_USER'),
  (2, 'Bob Smith', 'bob@example.com', '$2y$10$6ZjdZZiQ1KFxMcwQBRqlNuER7CBm5jRHHs6rKe..VaL3g1IQiwaIO', 'ROLE_USER'),
  (3, 'Demo Admin', 'admin@example.com', '$2y$10$Nr/mCHjTwVBBAq7YVFmL5unw8vseHX7rjvma4oaOaQuRhEUKlS/Ze', 'ROLE_ADMIN')
ON CONFLICT (id) DO NOTHING;

-- The rows above use explicit ids, which doesn't advance the IDENTITY sequences.
-- Move each sequence past the highest id, otherwise the next insert from the app fails with a duplicate key.
SELECT setval(pg_get_serial_sequence('hotels', 'id'), GREATEST((SELECT MAX(id) FROM hotels), 1));
SELECT setval(pg_get_serial_sequence('rooms', 'id'), GREATEST((SELECT MAX(id) FROM rooms), 1));
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 1));
