package com.onmyway.api.dto;

import com.onmyway.data.entities.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record PlaceResponse(
        Long id, Long version, Long cityId, String name, String description,
        BigDecimal latitude, BigDecimal longitude, PlaceStatus status,
        String rejectionReason,
        List<PhotoResponse> photos, List<ContactResponse> contacts,
        List<OpeningHoursResponse> openingHours) {

    public static PlaceResponse from(Place place) {
        return new PlaceResponse(
                place.getId(), place.getVersion(), place.getCity().getId(), place.getName(), place.getDescription(),
                place.getLatitude(), place.getLongitude(), place.getStatus(), place.getRejectionReason(),
                place.getPhotos().stream().map(PhotoResponse::from).toList(),
                place.getContacts().stream().map(ContactResponse::from).toList(),
                place.getOpeningHours().stream().map(OpeningHoursResponse::from).toList());
    }

    public record PhotoResponse(Long id, String url, Integer position, String description) {
        static PhotoResponse from(PlacePhoto photo) {
            return new PhotoResponse(photo.getId(), photo.getUrl(), photo.getPosition(), photo.getDescription());
        }
    }

    public record ContactResponse(Long id, PlaceContactType type, String value) {
        static ContactResponse from(PlaceContact contact) {
            return new ContactResponse(contact.getId(), contact.getType(), contact.getValue());
        }
    }

    public record OpeningHoursResponse(Long id, DayOfWeek dayOfWeek, LocalTime openingTime,
                                       LocalTime closingTime, boolean closed) {
        static OpeningHoursResponse from(PlaceOpeningHours hours) {
            return new OpeningHoursResponse(hours.getId(), hours.getDayOfWeek(), hours.getOpeningTime(),
                    hours.getClosingTime(), hours.isClosed());
        }
    }
}
