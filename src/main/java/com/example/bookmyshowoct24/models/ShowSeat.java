package com.example.bookmyshowoct24.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class ShowSeat extends BaseModel {
    // @JsonIgnore breaks the Show <-> ShowSeat cycle when serialising to JSON.
    // Without it, GET /api/shows/{id}/seats would recurse: show -> seats -> show -> seats -> ...
    @JsonIgnore
    @ManyToOne
    private Show show;

    @ManyToOne
    private Seat seat;

    @Enumerated(EnumType.ORDINAL)
    private ShowSeatStatus showSeatStatus;
}
