package com.example.hotelbooking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewResponse {
    private Long id;
    private Long hotelId;
    private String hotelName;
    /** Only set in the author's own list (GET /api/reviews/me), never in public listings. */
    private Long bookingId;
    /** Public display name, e.g. "Alice J." - never the email. */
    private String reviewerName;
    private Integer rating;
    private String comment;
    /** Month of the stay, "2026-10", shown as "Stayed October 2026". */
    private String stayMonth;
    private LocalDateTime createdAt;
    /** Set when the author edited the review. */
    private LocalDateTime updatedAt;
}
