package com.example.bookmyshowoct24.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity(name = "users")
public class User extends BaseModel {
    private String name;
    private String email;
    private String password;

    // mappedBy = "user" tells JPA: "don't create a new join table,
    // the Booking entity already has a user_id column — use that."
    // @JsonIgnore keeps user JSON small and prevents User <-> Booking cycles.
    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Booking> bookings;
}
