package com.onmyway.api.dto;

import com.onmyway.data.entities.PlaceContactType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record UpdatePlaceRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 10000) String description,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        List<@Valid CreatePlaceRequest.PhotoRequest> photos,
        List<@Valid CreatePlaceRequest.ContactRequest> contacts,
        List<@Valid CreatePlaceRequest.OpeningHoursRequest> openingHours) {}
