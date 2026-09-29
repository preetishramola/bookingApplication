package com.example.hotelbooking;

import com.example.hotelbooking.entity.Booking;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.Room;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.BookingStatus;
import com.example.hotelbooking.enums.Role;
import com.example.hotelbooking.enums.RoomType;
import com.example.hotelbooking.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.now();

    private User alice;
    private User bob;
    private String aliceToken;
    private String bobToken;
    private String adminToken;
    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        clearDatabase();

        alice = createUser("Alice Johnson", "alice@example.com", Role.ROLE_USER);
        bob = createUser("Bob Smith", "bob@example.com", Role.ROLE_USER);
        User admin = createUser("Admin", "admin@example.com", Role.ROLE_ADMIN);
        aliceToken = tokenFor(alice);
        bobToken = tokenFor(bob);
        adminToken = tokenFor(admin);

        hotel = new Hotel();
        hotel.setName("Harbor View");
        hotel.setCity("Boston");
        hotel.setRating(4.5);
        hotel = hotelRepository.save(hotel);

        room = new Room();
        room.setHotel(hotel);
        room.setRoomNumber("201");
        room.setType(RoomType.SUITE);
        room.setPricePerNight(260.0);
        room = roomRepository.save(room);
    }

    // ---------- Who may review ----------

    @Test
    void guestWhoStayedCanReviewAndHotelRatingBecomesTheReviewAverage() throws Exception {
        Booking stay = stay(alice, TODAY.minusDays(5), BookingStatus.CONFIRMED);

        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(3, "  Great harbor views.  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.comment").value("Great harbor views."))
                .andExpect(jsonPath("$.reviewerName").value("Alice J."))
                .andExpect(jsonPath("$.bookingId").value(stay.getId()));

        mockMvc.perform(get("/api/hotels/" + hotel.getId()))
                .andExpect(jsonPath("$.rating").value(3.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void reviewIsAllowedFromTheCheckInDay() throws Exception {
        Booking stay = stay(alice, TODAY, BookingStatus.CONFIRMED);
        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(5, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void cannotReviewBeforeCheckIn() throws Exception {
        Booking future = stay(alice, TODAY.plusDays(3), BookingStatus.CONFIRMED);
        mockMvc.perform(post(reviewUrl(future)).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(5, "Can't wait")))
                .andExpect(status().isConflict());
    }

    @Test
    void cannotReviewSomeoneElsesStay() throws Exception {
        Booking alicesStay = stay(alice, TODAY.minusDays(5), BookingStatus.CONFIRMED);
        mockMvc.perform(post(reviewUrl(alicesStay)).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(1, "fake")))
                .andExpect(status().isForbidden());
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void cannotReviewCancelledStay() throws Exception {
        Booking cancelled = stay(alice, TODAY.minusDays(5), BookingStatus.CANCELLED);
        mockMvc.perform(post(reviewUrl(cancelled)).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(4, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyOneReviewPerStay() throws Exception {
        Booking stay = stay(alice, TODAY.minusDays(5), BookingStatus.CONFIRMED);
        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(aliceToken))
                .contentType(MediaType.APPLICATION_JSON).content(reviewJson(4, null))).andExpect(status().isCreated());
        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(aliceToken))
                .contentType(MediaType.APPLICATION_JSON).content(reviewJson(5, null))).andExpect(status().isConflict());
        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    void writingRequiresLoginAndValidInput() throws Exception {
        Booking stay = stay(alice, TODAY.minusDays(5), BookingStatus.CONFIRMED);
        mockMvc.perform(post(reviewUrl(stay)).contentType(MediaType.APPLICATION_JSON).content(reviewJson(5, null)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(6, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.rating").exists());
        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Reading ----------

    @Test
    void anyoneCanReadReviewsWithoutPrivateDetails() throws Exception {
        review(alice, aliceToken, 5, "Loved it");
        review(bob, bobToken, 4, "Good");

        mockMvc.perform(get("/api/hotels/" + hotel.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].reviewerName").value("Bob S.")) // newest first
                .andExpect(jsonPath("$.items[1].reviewerName").value("Alice J."))
                .andExpect(jsonPath("$.items[0].bookingId").doesNotExist())
                .andExpect(jsonPath("$.items[0].stayMonth").isNotEmpty())
                .andExpect(content().string(not(containsString("@example.com"))));

        mockMvc.perform(get("/api/hotels/" + hotel.getId() + "/reviews?page=0&size=1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void readingReviewsValidatesInput() throws Exception {
        mockMvc.perform(get("/api/hotels/999999/reviews")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/hotels/" + hotel.getId() + "/reviews?size=500")).andExpect(status().isBadRequest());
    }

    @Test
    void myReviewsIncludeBookingIds() throws Exception {
        long bookingId = review(alice, aliceToken, 5, "Loved it");
        mockMvc.perform(get("/api/reviews/me").header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[0].hotelName").value("Harbor View"));
        mockMvc.perform(get("/api/reviews/me").header("Authorization", bearer(bobToken)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- Edit / delete ----------

    @Test
    void authorCanEditOwnReviewAndAverageFollows() throws Exception {
        review(alice, aliceToken, 2, "Meh");
        long reviewId = reviewRepository.findAll().get(0).getId();

        mockMvc.perform(put("/api/reviews/" + reviewId).header("Authorization", bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(1, "hijack")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/reviews/" + reviewId).header("Authorization", bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(4, "Better on day two")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/hotels/" + hotel.getId())).andExpect(jsonPath("$.rating").value(4.0));
    }

    @Test
    void deletingTheLastReviewRestoresTheInitialRating() throws Exception {
        review(alice, aliceToken, 2, "Meh");
        long reviewId = reviewRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/reviews/" + reviewId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/reviews/" + reviewId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/hotels/" + hotel.getId()))
                .andExpect(jsonPath("$.rating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void adminCanDeleteAnyReview() throws Exception {
        review(alice, aliceToken, 1, "abusive text");
        long reviewId = reviewRepository.findAll().get(0).getId();
        mockMvc.perform(delete("/api/reviews/" + reviewId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void cancellingTheBookingRemovesItsReview() throws Exception {
        long bookingId = review(alice, aliceToken, 1, "Terrible");

        mockMvc.perform(delete("/api/bookings/" + bookingId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isNoContent());

        assertThat(reviewRepository.count()).isZero();
        mockMvc.perform(get("/api/hotels/" + hotel.getId())).andExpect(jsonPath("$.rating").value(4.5));
    }

    // ---------- Rating in search ----------

    @Test
    void minRatingFilterUsesTheReviewAverage() throws Exception {
        mockMvc.perform(get("/api/hotels/search?minRating=4")).andExpect(jsonPath("$.length()").value(1));

        review(alice, aliceToken, 2, "Noisy");
        review(bob, bobToken, 3, "Okay");

        String body = mockMvc.perform(get("/api/hotels/search?minRating=4"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(body, "$.length()")).isZero();

        mockMvc.perform(get("/api/hotels/search?minRating=2.5"))
                .andExpect(jsonPath("$[0].rating").value(2.5))
                .andExpect(jsonPath("$[0].reviewCount").value(2));
    }

    // ---------- helpers ----------

    private Booking stay(User guest, LocalDate checkIn, BookingStatus status) {
        Booking booking = new Booking();
        booking.setUser(guest);
        booking.setRoom(room);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkIn.plusDays(2));
        booking.setTotalAmount(520.0);
        booking.setStatus(status);
        return bookingRepository.save(booking);
    }

    /** Creates a past stay for the guest and reviews it through the API. Returns the booking id. */
    private long review(User guest, String token, int rating, String comment) throws Exception {
        Booking stay = stay(guest, TODAY.minusDays(7), BookingStatus.CONFIRMED);
        mockMvc.perform(post(reviewUrl(stay)).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(reviewJson(rating, comment)))
                .andExpect(status().isCreated());
        return stay.getId();
    }

    private static String reviewUrl(Booking booking) {
        return "/api/bookings/" + booking.getId() + "/review";
    }

    private static String reviewJson(int rating, String comment) {
        return comment == null
                ? "{\"rating\":" + rating + "}"
                : "{\"rating\":" + rating + ",\"comment\":\"" + comment + "\"}";
    }
}
