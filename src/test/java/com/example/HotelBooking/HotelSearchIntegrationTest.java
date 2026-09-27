package com.example.hotelbooking;

import com.example.hotelbooking.dto.request.HotelRequest;
import com.example.hotelbooking.entity.Hotel;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.Role;
import com.example.hotelbooking.service.HotelService;
import com.example.hotelbooking.support.FakeEmbeddingModel;
import com.example.hotelbooking.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelSearchIntegrationTest extends IntegrationTestBase {

    // Cubbon Park, Bangalore
    private static final String PARK_LAT = "12.9763";
    private static final String PARK_LNG = "77.5929";

    @Autowired private HotelService hotelService;
    @Autowired private EmbeddingModel embeddingModel;

    private String adminToken;

    @BeforeEach
    void setUp() {
        clearDatabase();
        User admin = createUser("Admin", "admin@example.com", Role.ROLE_ADMIN);
        adminToken = tokenFor(admin);

        // ~0.4 km from the park
        addHotel("The Cubbon Courtyard", 12.9745, 77.5960,
                "A quiet, romantic boutique hotel for couples with a great breakfast", List.of("Gourmet breakfast", "Rooftop garden"));
        // ~1.5 km from the park
        addHotel("MG Road Grand", 12.9756, 77.6069,
                "A lively family hotel with a big pool and kids club, close to nightlife", List.of("Pool", "Kids club"));
        // ~17 km from the park
        addHotel("Whitefield Tech Suites", 12.9698, 77.7500,
                "Business hotel with meeting rooms and a co-working lounge", List.of("Meeting rooms"));
        // Other side of the world
        addHotel("Harbor View", 42.3601, -71.0589,
                "Waterfront hotel by the sea with harbor views", List.of("Harbor view"));
        // No coordinates: can appear in vibe search but never in geo search
        addHotel("Hidden Spa", null, null, "A quiet spa and yoga retreat", List.of("Spa"));
    }

    @AfterEach
    void restoreEmbeddingModel() {
        fake().setUnavailable(false);
    }

    // ---------- Geo search (PostGIS) ----------

    @Test
    void nearbyReturnsHotelsInsideRadiusNearestFirst() throws Exception {
        mockMvc.perform(get("/api/hotels/nearby").param("lat", PARK_LAT).param("lng", PARK_LNG).param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].hotel.name").value("The Cubbon Courtyard"))
                .andExpect(jsonPath("$[0].distanceKm").value(lessThan(1.0)))
                .andExpect(jsonPath("$[1].hotel.name").value("MG Road Grand"))
                .andExpect(jsonPath("$[0].matchScore").doesNotExist());
    }

    @Test
    void biggerRadiusIncludesFartherHotels() throws Exception {
        mockMvc.perform(get("/api/hotels/nearby").param("lat", PARK_LAT).param("lng", PARK_LNG).param("radiusKm", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].hotel.name").value("Whitefield Tech Suites"));
    }

    @Test
    void nearbyDefaultsToFiveKilometres() throws Exception {
        mockMvc.perform(get("/api/hotels/nearby").param("lat", PARK_LAT).param("lng", PARK_LNG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void nearbyValidatesInput() throws Exception {
        mockMvc.perform(get("/api/hotels/nearby").param("lat", "100").param("lng", PARK_LNG))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/nearby").param("lat", PARK_LAT))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/nearby").param("lat", PARK_LAT).param("lng", PARK_LNG).param("radiusKm", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/nearby").param("lat", PARK_LAT).param("lng", PARK_LNG).param("radiusKm", "5000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/nearby").param("lat", "abc").param("lng", PARK_LNG))
                .andExpect(status().isBadRequest());
    }

    // ---------- Vibe search (pgvector) ----------

    @Test
    void vibeSearchRanksByMeaning() throws Exception {
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "a quiet romantic boutique place with great breakfast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hotel.name").value("The Cubbon Courtyard"))
                .andExpect(jsonPath("$[0].matchScore").isNumber())
                .andExpect(jsonPath("$[0].distanceKm").doesNotExist());

        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "somewhere with meeting rooms for a business trip"))
                .andExpect(jsonPath("$[0].hotel.name").value("Whitefield Tech Suites"));

        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "spa and yoga").param("limit", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].hotel.name").value("Hidden Spa"));
    }

    @Test
    void vibeSearchCanBeLimitedToARadius() throws Exception {
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "business hotel with meeting rooms")
                        .param("lat", PARK_LAT).param("lng", PARK_LNG).param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].hotel.name", not(hasItem("Whitefield Tech Suites"))))
                .andExpect(jsonPath("$[0].distanceKm").isNumber())
                .andExpect(jsonPath("$[0].matchScore").isNumber());
    }

    @Test
    void vibeSearchValidatesInput() throws Exception {
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "  "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "x".repeat(501)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "romantic").param("lat", PARK_LAT))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "romantic").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vibeSearchReturns503WhenEmbeddingModelIsDown() throws Exception {
        fake().setUnavailable(true);
        mockMvc.perform(get("/api/hotels/vibe-search").param("q", "romantic"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void hotelIsStillCreatedWhenEmbeddingModelIsDownAndCanBeEmbeddedLater() throws Exception {
        fake().setUnavailable(true);

        mockMvc.perform(post("/api/admin/hotels/add").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Late Hotel","city":"Bangalore","latitude":12.97,"longitude":77.59,
                                 "description":"romantic rooftop hotel","amenities":["Rooftop"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(12.97))
                .andExpect(jsonPath("$.amenities[0]").value("Rooftop"));
        assertThat(hotelRepository.findIdsWithoutEmbedding()).hasSize(1);

        mockMvc.perform(post("/api/admin/hotels/embeddings/refresh").header("Authorization", bearer(adminToken)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.failed").value(1));

        fake().setUnavailable(false);
        mockMvc.perform(post("/api/admin/hotels/embeddings/refresh").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedded").value(1));
        assertThat(hotelRepository.findIdsWithoutEmbedding()).isEmpty();
    }

    @Test
    void refreshEmbeddingsIsAdminOnly() throws Exception {
        User user = createUser("User", "user@example.com", Role.ROLE_USER);
        mockMvc.perform(post("/api/admin/hotels/embeddings/refresh").header("Authorization", bearer(tokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void addHotelRequiresLatitudeAndLongitudeTogether() throws Exception {
        mockMvc.perform(post("/api/admin/hotels/add").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Half Located","city":"Bangalore","latitude":12.97}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/hotels/add").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Off Map","city":"Nowhere","latitude":95,"longitude":10}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.latitude").exists());
    }

    // ---------- Helpers ----------

    private FakeEmbeddingModel fake() {
        return (FakeEmbeddingModel) embeddingModel;
    }

    private Hotel addHotel(String name, Double lat, Double lng, String description, List<String> amenities) {
        HotelRequest request = new HotelRequest();
        request.setName(name);
        request.setCity("Somewhere");
        request.setLatitude(lat);
        request.setLongitude(lng);
        request.setDescription(description);
        request.setAmenities(amenities);
        return hotelService.addHotel(request);
    }
}
