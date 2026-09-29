package com.example.hotelbooking.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** A stable JSON shape for paged lists (Spring's Page serialises its internals). */
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
