package com.onmyway.api.dto;

import com.onmyway.data.entities.PlaceContactType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record CreatePlaceRequest(
        @NotNull Long cityId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 10000) String description,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        List<@Valid PhotoRequest> photos,
        List<@Valid ContactRequest> contacts,
        List<@Valid OpeningHoursRequest> openingHours) {

    public record PhotoRequest(@NotBlank @Size(max = 2048) String url,
                               @NotNull @PositiveOrZero Integer position,
                               @Size(max = 1000) String description) {}

    public record ContactRequest(@NotNull PlaceContactType type, @NotBlank @Size(max = 2048) String value) {}

    public record OpeningHoursRequest(@NotNull DayOfWeek dayOfWeek, LocalTime openingTime,
                                      LocalTime closingTime, boolean closed) {}
}
