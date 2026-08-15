package com.example.hotelbooking.entity;

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

    // A hotel can have many rooms. CascadeType.ALL means if we save/delete a hotel, 
    // it cascades to its rooms.
    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Room> rooms = new ArrayList<>();
}