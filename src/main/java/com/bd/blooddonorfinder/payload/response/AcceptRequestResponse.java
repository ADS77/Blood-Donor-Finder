package com.bd.blooddonorfinder.payload.response;

import java.util.UUID;

/** Returned to the donor who won the accept race — chat is immediately usable. */
public record AcceptRequestResponse(
        UUID requestId,
        UUID chatId,
        UUID requesterId,
        UUID donorId,
        String status) {
}
