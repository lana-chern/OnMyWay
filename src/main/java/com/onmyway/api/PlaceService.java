package com.onmyway.api;

import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import com.onmyway.api.exception.ResourceNotFoundException;
import com.onmyway.data.entities.*;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class PlaceService {
    private final PlaceRepository placeRepository;
    private final CityRepository cityRepository;

    public PlaceService(PlaceRepository placeRepository, CityRepository cityRepository) {
        this.placeRepository = placeRepository;
        this.cityRepository = cityRepository;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> findPublished(Long cityId) {
        if (!cityRepository.existsById(cityId)) {
            throw new ResourceNotFoundException("City %d not found".formatted(cityId));
        }
        return placeRepository.findByCityIdAndStatus(cityId, PlaceStatus.PUBLISHED)
                .stream().map(PlaceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlaceResponse findById(Long id) {
        return placeRepository.findById(id)
                .filter(place -> place.getStatus() == PlaceStatus.PUBLISHED)
                .map(PlaceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
    }

    public PlaceResponse create(CreatePlaceRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City %d not found".formatted(request.cityId())));
        Place place = new Place();
        place.setCity(city);
        apply(place, request.name(), request.description(), request.latitude(), request.longitude(),
                request.photos(), request.contacts(), request.openingHours());
        return PlaceResponse.from(placeRepository.save(place));
    }

    public PlaceResponse update(Long id, UpdatePlaceRequest request) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        apply(place, request.name(), request.description(), request.latitude(), request.longitude(),
                request.photos(), request.contacts(), request.openingHours());
        return PlaceResponse.from(place);
    }

    private void apply(Place place, String name, String description, java.math.BigDecimal latitude,
                       java.math.BigDecimal longitude, List<CreatePlaceRequest.PhotoRequest> photos,
                       List<CreatePlaceRequest.ContactRequest> contacts,
                       List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        place.setName(name);
        place.setDescription(description);
        place.setLatitude(latitude);
        place.setLongitude(longitude);
        validateOpeningHours(openingHours);

        place.getPhotos().clear();
        if (photos != null) {
            for (var request : photos) {
                PlacePhoto photo = new PlacePhoto();
                photo.setPlace(place);
                photo.setUrl(request.url());
                photo.setPosition(request.position());
                photo.setDescription(request.description());
                place.getPhotos().add(photo);
            }
        }

        place.getContacts().clear();
        if (contacts != null) {
            for (var request : contacts) {
                PlaceContact contact = new PlaceContact();
                contact.setPlace(place);
                contact.setType(request.type());
                contact.setValue(request.value());
                place.getContacts().add(contact);
            }
        }

        place.getOpeningHours().clear();
        if (openingHours != null) {
            for (var request : openingHours) {
                PlaceOpeningHours hours = new PlaceOpeningHours();
                hours.setPlace(place);
                hours.setDayOfWeek(request.dayOfWeek());
                hours.setOpeningTime(request.openingTime());
                hours.setClosingTime(request.closingTime());
                hours.setClosed(request.closed());
                place.getOpeningHours().add(hours);
            }
        }
    }

    private void validateOpeningHours(List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        if (openingHours == null) return;
        for (var hours : openingHours) {
            if (hours.closed()) {
                if (hours.openingTime() != null || hours.closingTime() != null) {
                    throw new IllegalArgumentException("Closed opening hours must not contain times");
                }
            } else if (hours.openingTime() == null || hours.closingTime() == null
                    || !hours.openingTime().isBefore(hours.closingTime())) {
                throw new IllegalArgumentException("Opening time must be before closing time for an open interval");
            }
        }
    }
}
