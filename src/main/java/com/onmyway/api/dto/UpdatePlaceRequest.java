package com.onmyway.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Fields to update for an existing place. The version provides optimistic concurrency control.")
public record UpdatePlaceRequest(
        @Schema(description = "Current version of the place. Must match the version last read by the client.", example = "3")
        Long version,
        @Schema(description = "Updated display name of the place.", example = "Samara Regional Museum")
        String name,
        @Schema(description = "Updated description of the place.")
        String description,
        @Schema(description = "Updated latitude in decimal degrees.", example = "53.1959")
        BigDecimal latitude,
        @Schema(description = "Updated longitude in decimal degrees.", example = "50.1002")
        BigDecimal longitude,
        @Schema(description = "Replacement list of photos.")
        List<CreatePlaceRequest.PhotoRequest> photos,
        @Schema(description = "Replacement list of contact details.")
        List<CreatePlaceRequest.ContactRequest> contacts,
        @Schema(description = "Replacement list of opening hours.")
        List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {}
