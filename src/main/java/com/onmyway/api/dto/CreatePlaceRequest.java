package com.onmyway.api.dto;

import com.onmyway.data.entities.PlaceContactType;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record CreatePlaceRequest(
        Long cityId, String name, String description, BigDecimal latitude, BigDecimal longitude,
        List<PhotoRequest> photos, List<ContactRequest> contacts, List<OpeningHoursRequest> openingHours) {
    public record PhotoRequest(String url, Integer position, String description) {}
    public record ContactRequest(PlaceContactType type, String value) {}
    public record OpeningHoursRequest(DayOfWeek dayOfWeek, LocalTime openingTime,
                                      LocalTime closingTime, boolean closed) {}
}
