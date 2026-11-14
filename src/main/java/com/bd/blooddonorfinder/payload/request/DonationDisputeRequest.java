package com.bd.blooddonorfinder.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** Requester's rejection of a completion claim. */
@Data
public class DonationDisputeRequest implements Serializable {

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason can't exceed 500 characters")
    private String reason;
}
