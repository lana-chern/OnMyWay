package com.onmyway.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PublishedPlacesPageResponse(
        List<PlaceResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static PublishedPlacesPageResponse from(Page<PlaceResponse> result) {
        return new PublishedPlacesPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast());
    }
}
