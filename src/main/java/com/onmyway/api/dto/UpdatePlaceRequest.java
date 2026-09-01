package com.onmyway.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePlaceRequest(
        String name, String description, BigDecimal latitude, BigDecimal longitude,
        List<CreatePlaceRequest.PhotoRequest> photos,
        List<CreatePlaceRequest.ContactRequest> contacts,
        List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {}
