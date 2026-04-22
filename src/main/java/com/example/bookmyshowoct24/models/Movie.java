package com.example.bookmyshowoct24.models;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity(name = "movies")
public class Movie extends BaseModel {
    private String name;
    private double rating;
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    private Category category;

    // @ElementCollection stores the list in a separate join table (movie_languages).
    // Each row: (movie_id, language). Lets a movie be in Hindi + Tamil + English.
    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Language> languages;
}
