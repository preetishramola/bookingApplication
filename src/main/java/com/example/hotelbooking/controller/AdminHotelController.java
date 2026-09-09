package com.example.hotelbooking.controller;

import com.example.hotelbooking.dto.request.HotelRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/hotels")
public class AdminHotelController {

    @PostMapping("/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> addHotel(@RequestBody HotelRequest request) {
        return ResponseEntity.ok("Hotel added successfully");
    }
}