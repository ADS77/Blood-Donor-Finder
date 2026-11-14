package com.bd.blooddonorfinder.payload.response;

/** Outcome of an admin verification decision. */
public record DocumentVerificationResult(
        DocumentDto document,
        boolean donorVerified) {
}
