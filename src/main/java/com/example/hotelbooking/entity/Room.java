package com.example.hotelbooking.entity;

import com.example.hotelbooking.enums.RoomStatus;
import com.example.hotelbooking.enums.RoomType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    private RoomType type;

    @Column(nullable = false)
    private Double pricePerNight;

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.AVAILABLE;

    // The foreign key column will be 'hotel_id'
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
}