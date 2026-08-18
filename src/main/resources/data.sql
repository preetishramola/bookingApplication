-- Seed data for Hotel Booking demo application
INSERT INTO hotels (id, name, address, city, state, country, rating) VALUES
  (1, 'Sunset Inn', '10 Main Street', 'New York', 'NY', 'USA', 4.5),
  (2, 'Harbor View', '22 Bay Road', 'Boston', 'MA', 'USA', 4.7)
ON CONFLICT (id) DO NOTHING;

INSERT INTO rooms (id, hotel_id, room_number, type, price_per_night, status) VALUES
  (1, 1, '101', 'STANDARD', 120.00, 'AVAILABLE'),
  (2, 1, '102', 'DELUXE', 180.00, 'AVAILABLE'),
  (3, 2, '201', 'SUITE', 260.00, 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, name, email) VALUES
  (1, 'Alice Johnson', 'alice@example.com'),
  (2, 'Bob Smith', 'bob@example.com')
ON CONFLICT (id) DO NOTHING;
