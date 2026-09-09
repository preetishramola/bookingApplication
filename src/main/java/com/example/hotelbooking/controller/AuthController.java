package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.AuthRequest;
import com.example.hotelbooking.dto.AuthResponse;
import com.example.hotelbooking.dto.RegisterRequest;
import com.example.hotelbooking.entity.User;
import com.example.hotelbooking.enums.Role;
import com.example.hotelbooking.repository.UserRepository;
import com.example.hotelbooking.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Make sure to add PasswordEncoder to the constructor!
    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        // 1. Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email is already in use!");
        }

        // 2. Create the new user
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        // 3. Hash the password before saving!
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Default to USER role if none is provided
        user.setRole(request.getRole() != null ? request.getRole() : Role.ROLE_USER);

        // 4. Save to PostgreSQL
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Updated to pass the user's name into the token!
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getName());

        return ResponseEntity.ok(new AuthResponse(token));
    }
}