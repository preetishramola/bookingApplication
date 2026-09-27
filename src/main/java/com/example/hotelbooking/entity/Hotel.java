package com.example.hotelbooking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotels")
@Getter @Setter @NoArgsConstructor
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    
    private String address;
    private String city;
    private String state;
    private String country;
    private Double rating;

    @Column(columnDefinition = "TEXT")
    private String description;

    // WGS84 coordinates. The PostGIS `location` geography column is GENERATED from these
    // (see db/postgis-pgvector.sql), so it is never written from Java.
    private Double latitude;
    private Double longitude;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "hotel_amenities",
            joinColumns = @JoinColumn(name = "hotel_id")
    )
    @Column(name = "amenity", nullable = false)
    private List<String> amenities = new ArrayList<>();

    // Note: the pgvector `embedding` column is intentionally NOT mapped here, so loading a hotel
    // doesn't drag 768 floats along. It is read/written only through native queries in HotelRepository.

    // A hotel can have many rooms. CascadeType.ALL means if we save/delete a hotel, 
    // it cascades to its rooms.
    @JsonIgnore
    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms = new ArrayList<>();
}