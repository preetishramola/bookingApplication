package com.example.hotelbooking;

import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.Role;
import com.example.hotelbooking.enums.RoomType;
import com.example.hotelbooking.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingFlowIntegrationTest extends IntegrationTestBase {

    // A future Monday, so a 2-night stay uses standard (non-weekend) pricing
    private static final LocalDate CHECK_IN = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    private User alice;
    private String aliceToken;
    private String bobToken;
    private String adminToken;
    private Room room;

    @BeforeEach
    void setUp() {
        clearDatabase();

        alice = createUser("Alice", "alice@example.com", Role.ROLE_USER);
        User bob = createUser("Bob", "bob@example.com", Role.ROLE_USER);
        User admin = createUser("Admin", "admin@example.com", Role.ROLE_ADMIN);
        aliceToken = tokenFor(alice);
        bobToken = tokenFor(bob);
        adminToken = tokenFor(admin);

        Hotel hotel = new Hotel();
        hotel.setName("Sunset Inn");
        hotel.setCity("New York");
        hotel.setCountry("USA");
        hotel.setRating(4.5);
        hotel = hotelRepository.save(hotel);

        room = new Room();
        room.setHotel(hotel);
        room.setRoomNumber("101");
        room.setType(RoomType.STANDARD);
        room.setPricePerNight(120.0);
        room = roomRepository.save(room);
    }

    // ---------- Auth ----------

    @Test
    void registerThenLoginReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Carol","email":"carol@example.com","password":"secret123"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"carol@example.com","password":"secret123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registrationCannotGrantAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mallory","email":"mallory@example.com","password":"secret123","role":"ROLE_ADMIN"}"""))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail("mallory@example.com").orElseThrow().getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"1"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").exists());
    }

    // ---------- Access control ----------

    @Test
    void bookingEndpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/bookings/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/bookings/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/1/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/bookings").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson())).andExpect(status().isUnauthorized());
    }

    @Test
    void hotelBrowsingIsPublic() throws Exception {
        mockMvc.perform(get("/api/hotels/" + room.getHotel().getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sunset Inn"));
        mockMvc.perform(get("/api/hotels/" + room.getHotel().getId() + "/rooms")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/health")).andExpect(status().isOk());
    }

    @Test
    void usersCanOnlySeeAndCancelTheirOwnBookings() throws Exception {
        long bookingId = createBookingAs(aliceToken, UUID.randomUUID().toString());

        mockMvc.perform(get("/api/bookings/" + bookingId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/bookings/" + bookingId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/" + alice.getId() + "/bookings").header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bookings/" + bookingId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/" + bookingId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/bookings/me").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- Booking flow ----------

    @Test
    void createBookingPricesStayWithAddOnsForTheLoggedInUser() throws Exception {
        performBooking(aliceToken, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(120.0 * 2 + 30)) // 2 nights + breakfast
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.hotelName").value("Sunset Inn"));

        assertThat(bookingRepository.findByUserId(alice.getId())).hasSize(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void overlappingBookingIsRejected() throws Exception {
        createBookingAs(aliceToken, UUID.randomUUID().toString());

        performBooking(bobToken, UUID.randomUUID().toString())
                .andExpect(status().isConflict());
    }

    @Test
    void cancelledBookingFreesTheRoomAndCannotBeCancelledTwice() throws Exception {
        long bookingId = createBookingAs(aliceToken, UUID.randomUUID().toString());

        mockMvc.perform(delete("/api/bookings/" + bookingId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/bookings/" + bookingId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isConflict());

        performBooking(bobToken, UUID.randomUUID().toString())
                .andExpect(status().isCreated());
    }

    // ---------- Idempotency ----------

    @Test
    void duplicateIdempotencyKeyIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        performBooking(aliceToken, key).andExpect(status().isCreated());
        performBooking(aliceToken, key).andExpect(status().isConflict());

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void failedRequestReleasesIdempotencyKeySoItCanBeRetried() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/bookings").header("Authorization", bearer(aliceToken)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson().replace("\"CREDIT_CARD\"", "\"BITCOIN\"")))
                .andExpect(status().isBadRequest());

        performBooking(aliceToken, key).andExpect(status().isCreated());
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/bookings").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(bookingJson()))
                .andExpect(status().isBadRequest());
    }

    // ---------- Admin + search ----------

    @Test
    void adminCanAddHotelAndRoomsButUsersCannot() throws Exception {
        String hotelJson = """
                {"name":"Harbor View","city":"Boston","state":"MA","country":"USA","rating":4.7}""";

        mockMvc.perform(post("/api/admin/hotels/add").header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(hotelJson))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/admin/hotels/add").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(hotelJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        long hotelId = ((Number) JsonPath.read(body, "$.id")).longValue();

        String roomJson = """
                {"roomNumber":"201","type":"SUITE","pricePerNight":260}""";
        mockMvc.perform(post("/api/admin/hotels/" + hotelId + "/rooms").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(roomJson))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/hotels/" + hotelId + "/rooms").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(roomJson))
                .andExpect(status().isBadRequest()); // duplicate room number

        assertThat(roomRepository.findByHotelId(hotelId)).hasSize(1);
    }

    @Test
    void addHotelValidatesInput() throws Exception {
        mockMvc.perform(post("/api/admin/hotels/add").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"","rating":9}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.city").exists())
                .andExpect(jsonPath("$.rating").exists());
    }

    @Test
    void searchFiltersHotels() throws Exception {
        Hotel other = new Hotel();
        other.setName("Harbor View");
        other.setCity("Boston");
        other.setCountry("USA");
        other.setRating(3.9);
        hotelRepository.save(other);

        mockMvc.perform(get("/api/hotels/search").param("city", "new york"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Sunset Inn"));
        mockMvc.perform(get("/api/hotels/search").param("name", "harbor"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].city").value("Boston"));
        mockMvc.perform(get("/api/hotels/search").param("minRating", "4"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/hotels/search"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Sunset Inn")); // sorted by rating desc
    }

    // ---------- Helpers ----------

    private String bookingJson() {
        return """
                {"roomId":%d,"checkIn":"%s","checkOut":"%s","addOns":["BREAKFAST"],"paymentMethod":"CREDIT_CARD"}"""
                .formatted(room.getId(), CHECK_IN, CHECK_OUT);
    }

    private ResultActions performBooking(String token, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson()));
    }

    private long createBookingAs(String token, String idempotencyKey) throws Exception {
        String body = performBooking(token, idempotencyKey)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.bookingId")).longValue();
    }
}
