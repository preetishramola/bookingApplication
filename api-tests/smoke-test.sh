#!/usr/bin/env bash
# End-to-end smoke test for the Hotel Booking API, using the demo data seeded by data.sql.
#
#   docker compose up -d
#   RATE_LIMIT_PER_MINUTE=1000 ./mvnw spring-boot:run      # the default 10 req/min is too low for this script
#   ./api-tests/smoke-test.sh                              # or: BASE_URL=http://localhost:8081 ./api-tests/smoke-test.sh
#
# Safe to re-run: every run registers a fresh user and books a room the admin creates in that same run.
# Requires: curl, jq, python3 (used only for date math).

set -u
BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_ID="$(date +%s)"
PASS=0
FAIL=0
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

# ---------- helpers ----------

# req METHOD PATH [TOKEN] [JSON_BODY] [IDEMPOTENCY_KEY]  -> sets STATUS and BODY
req() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}" key="${5:-}"
  local args=(-s -o "$TMP" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$key" ] && args+=(-H "Idempotency-Key: $key")
  [ -n "$body" ] && args+=(-H "Content-Type: application/json" -d "$body")
  STATUS="$(curl "${args[@]}")"
  BODY="$(cat "$TMP")"
  if [ "$STATUS" = "429" ]; then
    echo "!! Got 429 Too Many Requests. Restart the app with RATE_LIMIT_PER_MINUTE=1000 and re-run." >&2
    exit 2
  fi
}

# check "description" EXPECTED_STATUS [jq expression that must be true]
check() {
  local name="$1" expected="$2" condition="${3:-}"
  local ok=1
  [ "$STATUS" = "$expected" ] || ok=0
  if [ "$ok" = 1 ] && [ -n "$condition" ]; then
    [ "$(echo "$BODY" | jq -r "$condition" 2>/dev/null)" = "true" ] || ok=0
  fi
  if [ "$ok" = 1 ]; then
    PASS=$((PASS + 1)); printf '  \033[32mPASS\033[0m %s\n' "$name"
  else
    FAIL=$((FAIL + 1)); printf '  \033[31mFAIL\033[0m %s (expected %s, got %s)\n       %s\n' "$name" "$expected" "$STATUS" "$(echo "$BODY" | head -c 300)"
  fi
}

section() { printf '\n\033[1m%s\033[0m\n' "$1"; }
# login EMAIL PASSWORD -> sets TOKEN (plus STATUS/BODY, so it can be checked)
login() {
  req POST /api/auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"
  TOKEN="$(echo "$BODY" | jq -r '.token // empty')"
}
urlq() { jq -rn --arg v "$1" '$v|@uri'; }

# Monday-Wednesday of the week after next: 2 weekday nights -> standard pricing
CHECK_IN="$(python3 -c 'import datetime as d; t=d.date.today(); print(t + d.timedelta(days=(7 - t.weekday()) % 7 or 7) + d.timedelta(days=7))')"
CHECK_OUT="$(python3 -c "import datetime as d; print(d.date.fromisoformat('$CHECK_IN') + d.timedelta(days=2))")"
CUBBON_LAT=12.9763
CUBBON_LNG=77.5929

echo "Testing $BASE_URL  (run $RUN_ID, stay $CHECK_IN -> $CHECK_OUT)"

# ---------- 1. Health ----------
section "1. Health"
req GET /api/public/health
check "health endpoint is UP" 200 '.status == "UP"'
if [ "$STATUS" != "200" ]; then echo "App is not reachable at $BASE_URL"; exit 2; fi

# ---------- 2. Auth ----------
section "2. Auth"
USER_EMAIL="tester$RUN_ID@example.com"
req POST /api/auth/register "" "{\"name\":\"Test User\",\"email\":\"$USER_EMAIL\",\"password\":\"secret123\"}"
check "register a new user" 200
req POST /api/auth/register "" "{\"name\":\"Test User\",\"email\":\"$USER_EMAIL\",\"password\":\"secret123\"}"
check "registering the same email again is rejected" 400
req POST /api/auth/register "" '{"name":"","email":"not-an-email","password":"1"}'
check "invalid registration returns field errors" 400 'has("email") and has("password")'
req POST /api/auth/register "" "{\"name\":\"Sneaky\",\"email\":\"sneaky$RUN_ID@example.com\",\"password\":\"secret123\",\"role\":\"ROLE_ADMIN\"}"
check "register trying to become admin (role is ignored)" 200
login "sneaky$RUN_ID@example.com" secret123; SNEAKY_TOKEN="$TOKEN"

login "$USER_EMAIL" secret123; USER_TOKEN="$TOKEN"
check "login returns a JWT" 200 '.token | length > 20'
login alice@example.com password123; ALICE_TOKEN="$TOKEN"
check "seeded user alice can log in" 200
login admin@example.com admin123; ADMIN_TOKEN="$TOKEN"
check "seeded admin can log in" 200
req POST /api/auth/login "" '{"email":"alice@example.com","password":"wrong-password"}'
check "wrong password is rejected" 401

# ---------- 3. Browse hotels (public) ----------
section "3. Browse hotels (no login needed)"
req GET /api/hotels/3
check "get hotel 3 (The Cubbon Courtyard)" 200 '.name == "The Cubbon Courtyard" and (.amenities | length > 0)'
req GET /api/hotels/3/rooms
check "list rooms of hotel 3" 200 'length >= 2'
req GET /api/hotels/99999
check "unknown hotel returns 404" 404
req GET "/api/hotels/search?city=bangalore"
check "search by city (case-insensitive)" 200 'length >= 3 and all(.[]; .city == "Bangalore")'
req GET "/api/hotels/search?name=harbor"
check "search by partial name" 200 '.[0].name == "Harbor View"'
req GET "/api/hotels/search?minRating=4.6"
check "search by minimum rating" 200 'length >= 1 and all(.[]; .rating >= 4.6)'

# ---------- 4. Geo search (PostGIS) ----------
section "4. Geo search (PostGIS)"
req GET "/api/hotels/nearby?lat=$CUBBON_LAT&lng=$CUBBON_LNG&radiusKm=5"
check "hotels within 5 km of Cubbon Park, nearest first" 200 \
  '.[0].hotel.name == "The Cubbon Courtyard" and .[0].distanceKm < 1 and ([.[].hotel.name] | index("Whitefield Tech Suites") == null)'
req GET "/api/hotels/nearby?lat=$CUBBON_LAT&lng=$CUBBON_LNG&radiusKm=30"
check "30 km radius also finds Whitefield (~17 km)" 200 '[.[].hotel.name] | index("Whitefield Tech Suites") != null'
req GET "/api/hotels/nearby?lat=40.7128&lng=-74.0060&radiusKm=2"
check "hotels near New York" 200 '.[0].hotel.name == "Sunset Inn"'
req GET "/api/hotels/nearby?lat=200&lng=$CUBBON_LNG"
check "invalid latitude is rejected" 400
req GET "/api/hotels/nearby?lat=$CUBBON_LAT"
check "missing longitude is rejected" 400

# ---------- 5. Search by Vibe (pgvector + Ollama) ----------
section "5. Search by Vibe (pgvector + Ollama)"
Q="$(urlq "A quiet, romantic boutique hotel in Bangalore near Cubbon Park with a great breakfast")"
req GET "/api/hotels/vibe-search?q=$Q&limit=3"
if [ "$STATUS" = "503" ]; then
  echo "  (vibe search returned 503: is Ollama running? 'docker compose up -d' and wait for the model pull)"
fi
check "romantic boutique query -> The Cubbon Courtyard" 200 '.[0].hotel.name == "The Cubbon Courtyard" and .[0].matchScore > 0'
req GET "/api/hotels/vibe-search?q=$(urlq "peaceful yoga and spa escape away from the city")&limit=3"
check "yoga/spa query -> Nandi Hills Retreat" 200 '.[0].hotel.name == "Nandi Hills Retreat"'
req GET "/api/hotels/vibe-search?q=$(urlq "work trip with meeting rooms and airport shuttle")&limit=3"
check "business query -> Whitefield Tech Suites" 200 '.[0].hotel.name == "Whitefield Tech Suites"'
req GET "/api/hotels/vibe-search?q=$(urlq "business hotel with meeting rooms")&lat=$CUBBON_LAT&lng=$CUBBON_LNG&radiusKm=5"
check "vibe + 5 km radius excludes far-away Whitefield" 200 \
  '([.[].hotel.name] | index("Whitefield Tech Suites") == null) and all(.[]; .distanceKm <= 5)'
req GET "/api/hotels/vibe-search?q=%20%20"
check "empty vibe query is rejected" 400

# ---------- 6. Admin ----------
section "6. Admin"
HOTEL_JSON="{\"name\":\"Smoke Test Inn $RUN_ID\",\"address\":\"1 Church Street\",\"city\":\"Bangalore\",\"state\":\"Karnataka\",\"country\":\"India\",\"rating\":4.1,\"latitude\":12.9752,\"longitude\":77.6050,\"description\":\"Budget-friendly hotel on Church Street near cafes and bookstores, with free breakfast\",\"amenities\":[\"WiFi\",\"Free breakfast\"]}"
req POST /api/admin/hotels/add "" "$HOTEL_JSON"
check "add hotel without login -> 401" 401
req POST /api/admin/hotels/add "$USER_TOKEN" "$HOTEL_JSON"
check "add hotel as normal user -> 403" 403
req POST /api/admin/hotels/add "$SNEAKY_TOKEN" "$HOTEL_JSON"
check "user who asked for ROLE_ADMIN at signup is still not admin -> 403" 403
req POST /api/admin/hotels/add "$ADMIN_TOKEN" '{"name":"","rating":9,"latitude":12.9}'
check "invalid hotel returns field errors" 400 'has("name") and has("city") and has("rating")'
req POST /api/admin/hotels/add "$ADMIN_TOKEN" "$HOTEL_JSON"
check "admin adds a hotel" 201 '.id != null and .latitude == 12.9752'
NEW_HOTEL_ID="$(echo "$BODY" | jq -r '.id')"

req POST "/api/admin/hotels/$NEW_HOTEL_ID/rooms" "$ADMIN_TOKEN" '{"roomNumber":"S1","type":"DELUXE","pricePerNight":100}'
check "admin adds a room" 201 '.pricePerNight == 100'
ROOM_ID="$(echo "$BODY" | jq -r '.id')"
req POST "/api/admin/hotels/$NEW_HOTEL_ID/rooms" "$ADMIN_TOKEN" '{"roomNumber":"S1","type":"DELUXE","pricePerNight":100}'
check "duplicate room number is rejected" 400
req POST /api/admin/hotels/embeddings/refresh "$ADMIN_TOKEN"
check "refresh missing embeddings" 200 '.failed == 0'
req GET "/api/hotels/nearby?lat=$CUBBON_LAT&lng=$CUBBON_LNG&radiusKm=3"
check "new hotel shows up in geo search" 200 "[.[].hotel.id] | index($NEW_HOTEL_ID) != null"

# ---------- 7. Bookings ----------
section "7. Bookings (room $ROOM_ID, 100/night)"
BOOKING_JSON="{\"roomId\":$ROOM_ID,\"checkIn\":\"$CHECK_IN\",\"checkOut\":\"$CHECK_OUT\",\"addOns\":[\"BREAKFAST\"],\"paymentMethod\":\"UPI\"}"
KEY1="smoke-$RUN_ID-1"

req GET "/api/hotels/$NEW_HOTEL_ID/rooms/available?checkIn=$CHECK_IN&checkOut=$CHECK_OUT"
check "room is available before booking" 200 "[.[].id] | index($ROOM_ID) != null"
req POST /api/bookings "" "$BOOKING_JSON" "$KEY1"
check "booking without login -> 401" 401
req POST /api/bookings "$USER_TOKEN" "$BOOKING_JSON"
check "booking without Idempotency-Key -> 400" 400
req POST /api/bookings "$USER_TOKEN" '{"roomId": oops' "smoke-$RUN_ID-bad-json"
check "malformed JSON -> 400 (not a misleading 401)" 400
req POST /api/bookings "$USER_TOKEN" "${BOOKING_JSON/UPI/BITCOIN}" "$KEY1"
check "unsupported payment method -> 400" 400
req POST /api/bookings "$USER_TOKEN" "$BOOKING_JSON" "$KEY1"
check "create booking (same key retried after the failure) = 2 x 100 + 30 breakfast" 201 \
  '.totalAmount == 230 and .status == "CONFIRMED"'
BOOKING_ID="$(echo "$BODY" | jq -r '.bookingId')"
req POST /api/bookings "$USER_TOKEN" "$BOOKING_JSON" "$KEY1"
check "same Idempotency-Key again -> 409 duplicate" 409
req POST /api/bookings "$ALICE_TOKEN" "$BOOKING_JSON" "smoke-$RUN_ID-2"
check "alice books the same room/dates -> 409 overlap" 409
req GET "/api/hotels/$NEW_HOTEL_ID/rooms/available?checkIn=$CHECK_IN&checkOut=$CHECK_OUT"
check "room is no longer available for those dates" 200 "[.[].id] | index($ROOM_ID) == null"

req GET "/api/bookings/$BOOKING_ID" "$USER_TOKEN"
check "owner can view the booking" 200 ".bookingId == $BOOKING_ID"
req GET "/api/bookings/$BOOKING_ID" "$ALICE_TOKEN"
check "another user cannot view it -> 403" 403
req GET "/api/bookings/$BOOKING_ID" "$ADMIN_TOKEN"
check "admin can view it" 200
req GET /api/bookings/me "$USER_TOKEN"
check "my bookings lists it" 200 "[.[].bookingId] | index($BOOKING_ID) != null"
req GET /api/users/1/bookings "$USER_TOKEN"
check "cannot list alice's bookings -> 403" 403
req GET /api/users/1/bookings "$ADMIN_TOKEN"
check "admin can list alice's bookings" 200

req DELETE "/api/bookings/$BOOKING_ID" "$ALICE_TOKEN"
check "another user cannot cancel it -> 403" 403
req DELETE "/api/bookings/$BOOKING_ID" "$USER_TOKEN"
check "owner cancels the booking" 204
req DELETE "/api/bookings/$BOOKING_ID" "$USER_TOKEN"
check "cancelling twice -> 409" 409
req POST /api/bookings "$ALICE_TOKEN" "$BOOKING_JSON" "smoke-$RUN_ID-3"
check "after cancellation alice can book the same dates" 201

# ---------- Summary ----------
printf '\n\033[1m%d passed, %d failed\033[0m\n' "$PASS" "$FAIL"
[ "$FAIL" = 0 ]
