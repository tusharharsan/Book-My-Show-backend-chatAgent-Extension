package com.example.bookmyshowoct24.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity(name = "shows")
public class Show extends BaseModel {
    @ManyToOne
    private Movie movie;

    private Date startTime;
    private Date endTime;

    @Enumerated(EnumType.STRING)
    private Language language;

    @ManyToOne
    private Screen screen;

    @Enumerated(EnumType.ORDINAL)
    @ElementCollection
    private List<Feature> features;

    @OneToMany(mappedBy = "show")
    private List<ShowSeat> showSeats;
}

/*
Relationships
  Show -- Movie    M : 1
  Show -- Screen   M : 1
  Show -- ShowSeat 1 : M  (mappedBy = "show" means ShowSeat.show owns the FK column)
*/
