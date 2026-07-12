package com.ecommerce.controller.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope for list endpoints.
 * Returning this instead of the raw Spring {@code Page} keeps the JSON contract
 * explicit and avoids relying on the internal serialization of PageImpl.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
