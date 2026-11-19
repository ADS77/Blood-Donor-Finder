package com.bd.blooddonorfinder.payload.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable JSON envelope for offset-paginated JPA queries.
 *
 * <p>Spring Data's {@link Page} serializes with implementation details that are
 * easy to break with framework upgrades; controllers map it to this record
 * instead. Cursor-based endpoints (chat, notifications) keep their own response
 * shapes — this is for ordinary page/size navigation.
 */
public record PageResponse<T>(
        List<T> data,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }

    /** Empty page for requests that are valid but can match nothing. */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, false);
    }
}
