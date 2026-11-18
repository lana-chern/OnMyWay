package com.onmyway.api.dto;

import com.onmyway.data.entities.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record PlaceResponse(
        Long id, Long version, Long cityId, String name, String description,
        BigDecimal latitude, BigDecimal longitude, PlaceRevisionStatus status,
        String rejectionReason,
        List<PhotoResponse> photos, List<ContactResponse> contacts,
        List<OpeningHoursResponse> openingHours) {

    public static PlaceResponse from(Place place, PlaceRevision revision) {
        return new PlaceResponse(
                place.getId(), revision.getVersion(), place.getCity().getId(), revision.getName(),
                revision.getDescription(), revision.getLatitude(), revision.getLongitude(),
                revision.getStatus(), revision.getRejectionReason(),
                revision.getPhotos().stream().map(PhotoResponse::from).toList(),
                revision.getContacts().stream().map(ContactResponse::from).toList(),
                revision.getOpeningHours().stream().map(OpeningHoursResponse::from).toList());
    }

    public record PhotoResponse(Long id, String url, Integer position, String description) {
        static PhotoResponse from(PlaceRevisionPhoto photo) {
            return new PhotoResponse(photo.getId(), photo.getUrl(), photo.getPosition(), photo.getDescription());
        }
    }

    public record ContactResponse(Long id, PlaceContactType type, String value) {
        static ContactResponse from(PlaceRevisionContact contact) {
            return new ContactResponse(contact.getId(), contact.getType(), contact.getValue());
        }
    }

    public record OpeningHoursResponse(Long id, DayOfWeek dayOfWeek, LocalTime openingTime,
                                       LocalTime closingTime, boolean closed) {
        static OpeningHoursResponse from(PlaceRevisionOpeningHours hours) {
            return new OpeningHoursResponse(hours.getId(), hours.getDayOfWeek(), hours.getOpeningTime(),
                    hours.getClosingTime(), hours.isClosed());
        }
    }
}
