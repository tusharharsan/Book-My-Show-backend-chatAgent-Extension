package com.example.bookmyshowoct24.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity(name = "theatres")
public class Theatre extends BaseModel {
    private String name;
    private String address;

    // @JsonIgnore breaks the Screen <-> Theatre cycle. When serialising a Show
    // we want theatre name/address, but we don't want to then recurse back
    // through every screen in the theatre (which would loop forever).
    @JsonIgnore
    @OneToMany
    private List<Screen> screens;
}
