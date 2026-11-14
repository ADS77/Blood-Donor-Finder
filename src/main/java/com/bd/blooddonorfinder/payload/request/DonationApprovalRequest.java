package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** Requester's approval of a completed donation, optionally with a rating. */
@Data
public class DonationApprovalRequest implements Serializable {

    /** Optional 1–5 rating for the donor (feeds the donor's average). */
    @Min(value = 1, message = "rating must be between 1 and 5")
    @Max(value = 5, message = "rating must be between 1 and 5")
    private Integer rating;

    @Size(max = 500, message = "review can't exceed 500 characters")
    private String review;
}
