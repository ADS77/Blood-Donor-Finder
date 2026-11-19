package com.bd.blooddonorfinder.payload.enums;

/**
 * Which slice of blood requests a list call returns. Scoping is explicit so the
 * same endpoint can power the donor feed, the requester's own history and the
 * donor's accepted-request history without leaking other people's requests.
 */
public enum BloodRequestScope {

    /** Unclaimed requests any verified donor may accept. */
    OPEN,

    /** Requests created by the caller, in every status. */
    MINE,

    /** Requests the caller accepted as a donor, in every status. */
    ACCEPTED
}
