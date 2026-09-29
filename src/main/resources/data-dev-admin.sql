-- LOCAL DEV ONLY: admin@example.com / admin123. Not loaded by the prod profile (application-prod.yml),
-- because a well-known admin password on a public server lets anyone manage hotels.
INSERT INTO users (id, name, email, password, role) VALUES
  (3, 'Demo Admin', 'admin@example.com', '$2y$10$Nr/mCHjTwVBBAq7YVFmL5unw8vseHX7rjvma4oaOaQuRhEUKlS/Ze', 'ROLE_ADMIN')
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 1));
