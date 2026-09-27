package com.example.hotelbooking.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Stores a token bucket for each IP address
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // Configured via app.rate-limit.requests-per-minute in application.yml
    private final long requestsPerMinute;

    public RateLimitFilter(@Value("${app.rate-limit.requests-per-minute:10}") long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ipAddress = request.getRemoteAddr();
        Bucket bucket = cache.computeIfAbsent(ipAddress, this::createNewBucket);

        // Try to consume 1 token. If successful, allow the request.
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            // If the bucket is empty, reject the request with HTTP 429
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests. Please try again later.");
        }
    }

    private Bucket createNewBucket(String key) {
        // Refill the full allowance every minute. Max capacity is the same allowance.
        Refill refill = Refill.intervally(requestsPerMinute, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, refill);
        return Bucket.builder().addLimit(limit).build();
    }
}
