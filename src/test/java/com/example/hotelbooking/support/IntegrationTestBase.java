package com.example.hotelbooking.support;

import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.Role;
import com.example.hotelbooking.repository.*;
import com.example.hotelbooking.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup so every integration test reuses ONE Spring context and ONE Postgres container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JwtUtil jwtUtil;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected UserRepository userRepository;
    @Autowired protected HotelRepository hotelRepository;
    @Autowired protected RoomRepository roomRepository;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected PaymentRepository paymentRepository;
    @Autowired protected IdempotencyRepository idempotencyRepository;

    protected void clearDatabase() {
        paymentRepository.deleteAll();
        bookingRepository.deleteAll();
        idempotencyRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected User createUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRole(role);
        return userRepository.save(user);
    }

    protected String tokenFor(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getName());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
