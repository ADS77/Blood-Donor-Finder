package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

/** Donor's "I donated blood" submission. */
@Data
public class DonationCompleteRequest implements Serializable {

    /**
     * When the donation actually happened. Optional (defaults to now);
     * must not be in the future.
     */
    private Instant donatedAt;

    /** Optional note for the requester, e.g. ward/hospital details. */
    @Size(max = 500, message = "notes can't exceed 500 characters")
    private String notes;
}
