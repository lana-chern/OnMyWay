package com.onmyway.api.dto;

import com.onmyway.data.entities.PlaceContactType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Data required to create a place. The authenticated organizer becomes its owner.")
public record CreatePlaceRequest(
        @Schema(description = "ID of the city where the place is located.", example = "1")
        Long cityId,
        @Schema(description = "Display name of the place.", example = "Samara Regional Museum")
        String name,
        @Schema(description = "Description of the place and what visitors can expect.")
        String description,
        @Schema(description = "Latitude in decimal degrees.", example = "53.1959")
        BigDecimal latitude,
        @Schema(description = "Longitude in decimal degrees.", example = "50.1002")
        BigDecimal longitude,
        @Schema(description = "Optional photos. Position controls their display order.")
        List<PhotoRequest> photos,
        @Schema(description = "Optional contact details for the place.")
        List<ContactRequest> contacts,
        @Schema(description = "Optional opening hours by day of the week.")
        List<OpeningHoursRequest> openingHours) {
    @Schema(description = "Photo metadata.")
    public record PhotoRequest(
            @Schema(description = "Publicly accessible URL of the photo.", example = "https://example.com/place.jpg")
            String url,
            @Schema(description = "Zero-based display position.", example = "0")
            Integer position,
            @Schema(description = "Optional caption or description of the photo.")
            String description) {}

    @Schema(description = "A contact method for the place.")
    public record ContactRequest(
            @Schema(description = "Kind of contact, such as PHONE, EMAIL or WEBSITE.")
            PlaceContactType type,
            @Schema(description = "Contact value, such as a phone number, email address or URL.", example = "https://example.com")
            String value) {}

    @Schema(description = "Opening hours for a single day.")
    public record OpeningHoursRequest(
            @Schema(description = "Day to which these hours apply.", example = "MONDAY")
            DayOfWeek dayOfWeek,
            @Schema(description = "Local opening time in HH:mm format.", example = "09:00")
            LocalTime openingTime,
            @Schema(description = "Local closing time in HH:mm format.", example = "18:00")
            LocalTime closingTime,
            @Schema(description = "Set true if the place is closed for this day.")
            boolean closed) {}
}
