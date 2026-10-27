package com.onmyway.api;

import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import com.onmyway.api.exception.ResourceNotFoundException;
import com.onmyway.api.exception.StalePlaceVersionException;
import com.onmyway.data.entities.*;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceRepository;
import com.onmyway.data.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class PlaceService {
    private static final int MAX_REJECTION_REASON_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final PlaceRepository placeRepository;
    private final EntityManager entityManager;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;

    public PlaceService(PlaceRepository placeRepository, CityRepository cityRepository, UserRepository userRepository,
                        EntityManager entityManager) {
        this.placeRepository = placeRepository;
        this.entityManager = entityManager;
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<PlaceResponse> findPublished(Long cityId, String query, int page, int size) {
        if (cityId == null) {
            throw new IllegalArgumentException("cityId is required");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and %d".formatted(MAX_PAGE_SIZE));
        }
        if (!cityRepository.existsById(cityId)) {
            throw new ResourceNotFoundException("City %d not found".formatted(cityId));
        }
        String normalizedQuery = query == null || query.isBlank() ? null : escapeLikeWildcards(query.strip());
        return placeRepository.searchPublished(cityId, PlaceStatus.PUBLISHED, normalizedQuery, PageRequest.of(page, size))
                .map(PlaceResponse::from);
    }

    private String escapeLikeWildcards(String query) {
        return query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Transactional(readOnly = true)
    public PlaceResponse findById(Long id) {
        return placeRepository.findById(id).filter(place -> place.getStatus() == PlaceStatus.PUBLISHED)
                .map(PlaceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
    }

    @Transactional(readOnly = true)
    public PlaceResponse findForManagement(Long id, Long actorId, boolean canManageAnyPlace) {
        Place place = placeRepository.findById(id)
                .filter(candidate -> candidate.getStatus() != PlaceStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, canManageAnyPlace);
        return PlaceResponse.from(place);
    }

    public PlaceResponse create(CreatePlaceRequest request, Long ownerId) {
        validateRequest(request.cityId(), request.name(), request.latitude(), request.longitude(), request.photos(), request.contacts(), request.openingHours());
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City %d not found".formatted(request.cityId())));
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Place place = new Place();
        place.setCity(city);
        place.setOwner(owner);
        apply(place, request.name(), request.description(), request.latitude(), request.longitude(), request.photos(), request.contacts(), request.openingHours());
        return PlaceResponse.from(placeRepository.save(place));
    }

    public PlaceResponse update(Long id, UpdatePlaceRequest request, Long actorId, boolean canManageAnyPlace) {
        validateRequest(id, request.name(), request.latitude(), request.longitude(), request.photos(), request.contacts(), request.openingHours());
        Place place = placeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, canManageAnyPlace);
        if (place.getStatus() == PlaceStatus.ARCHIVED) {
            throw new ResourceNotFoundException("Place %d not found".formatted(id));
        }
        if (request.version() == null) {
            throw new IllegalArgumentException("version is required");
        }
        if (!Objects.equals(request.version(), place.getVersion())) {
            throw new StalePlaceVersionException(request.version(), place.getVersion());
        }

        // Inverse child-collection changes do not necessarily dirty the Place row.
        // Force the version only when scalar fields are unchanged; otherwise the normal
        // entity UPDATE advances it and an extra force-increment would be redundant.
        boolean scalarFieldsUnchanged = Objects.equals(place.getName(), request.name())
                && Objects.equals(place.getDescription(), request.description())
                && place.getLatitude().compareTo(request.latitude()) == 0
                && place.getLongitude().compareTo(request.longitude()) == 0;
        if (scalarFieldsUnchanged) {
            entityManager.lock(place, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        apply(place, request.name(), request.description(), request.latitude(), request.longitude(), request.photos(), request.contacts(), request.openingHours());
        placeRepository.flush();
        return scalarFieldsUnchanged
                ? PlaceResponse.from(place, place.getVersion() + 1)
                : PlaceResponse.from(place);
    }

    public void delete(Long id, Long actorId, boolean admin) {
        Place place = placeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, admin);
        place.setStatus(PlaceStatus.ARCHIVED);
    }

    public PlaceResponse submitForModeration(Long id, Long actorId) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, false);
        if (place.getStatus() != PlaceStatus.DRAFT && place.getStatus() != PlaceStatus.REJECTED) {
            throw new IllegalStateException("Only draft or rejected places can be submitted for moderation");
        }
        place.setRejectionReason(null);
        place.setStatus(PlaceStatus.PENDING_MODERATION);
        placeRepository.flush();
        return PlaceResponse.from(place);
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> findPendingModeration(boolean moderatorOrAdmin) {
        assertCanModerate(moderatorOrAdmin);
        return placeRepository.findByStatusOrderByCreatedAtAsc(PlaceStatus.PENDING_MODERATION)
                .stream().map(PlaceResponse::from).toList();
    }

    public PlaceResponse publish(Long id, boolean moderatorOrAdmin, Long expectedVersion) {
        return moderatePendingPlace(id, moderatorOrAdmin, PlaceStatus.PUBLISHED, null, expectedVersion);
    }

    public PlaceResponse reject(Long id, boolean moderatorOrAdmin, Long expectedVersion, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        String normalizedReason = reason.strip();
        if (normalizedReason.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new IllegalArgumentException("Rejection reason must be at most %d characters".formatted(MAX_REJECTION_REASON_LENGTH));
        }
        return moderatePendingPlace(id, moderatorOrAdmin, PlaceStatus.REJECTED, normalizedReason, expectedVersion);
    }

    private PlaceResponse moderatePendingPlace(Long id, boolean moderatorOrAdmin, PlaceStatus targetStatus,
                                               String rejectionReason, Long expectedVersion) {
        assertCanModerate(moderatorOrAdmin);
        if (expectedVersion == null) {
            throw new IllegalArgumentException("version is required");
        }
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        if (!Objects.equals(expectedVersion, place.getVersion())) {
            throw new StalePlaceVersionException(expectedVersion, place.getVersion());
        }
        if (place.getStatus() != PlaceStatus.PENDING_MODERATION) {
            throw new IllegalStateException("Only places pending moderation can be published or rejected");
        }
        place.setRejectionReason(rejectionReason);
        place.setStatus(targetStatus);
        // The @Version predicate protects against edits that race with this moderation decision.
        placeRepository.flush();
        return PlaceResponse.from(place);
    }

    private void assertCanModerate(boolean moderatorOrAdmin) {
        if (!moderatorOrAdmin) {
            throw new AccessDeniedException("Moderator or administrator role is required");
        }
    }

    private void assertCanManage(Place place, Long actorId, boolean canManageAnyPlace) {
        if (!canManageAnyPlace && (place.getOwner() == null || !Objects.equals(place.getOwner().getId(), actorId))) {
            throw new AccessDeniedException("You can manage only your own places");
        }
    }

    private void apply(Place place, String name, String description, BigDecimal latitude, BigDecimal longitude,
                       List<CreatePlaceRequest.PhotoRequest> photos, List<CreatePlaceRequest.ContactRequest> contacts,
                       List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        place.setName(name); place.setDescription(description); place.setLatitude(latitude); place.setLongitude(longitude);
        place.getPhotos().clear();
        if (photos != null) {
            for (var request : photos) {
                PlacePhoto photo = new PlacePhoto(); photo.setPlace(place); photo.setUrl(request.url()); photo.setPosition(request.position()); photo.setDescription(request.description()); place.getPhotos().add(photo);
            }
        }
        place.getContacts().clear();
        if (contacts != null) {
            for (var request : contacts) {
                PlaceContact contact = new PlaceContact(); contact.setPlace(place); contact.setType(request.type()); contact.setValue(request.value()); place.getContacts().add(contact);
            }
        }
        place.getOpeningHours().clear();
        if (openingHours != null) {
            for (var request : openingHours) {
                PlaceOpeningHours hours = new PlaceOpeningHours(); hours.setPlace(place); hours.setDayOfWeek(request.dayOfWeek()); hours.setOpeningTime(request.openingTime()); hours.setClosingTime(request.closingTime()); hours.setClosed(request.closed()); place.getOpeningHours().add(hours);
            }
        }
    }

    private void validateRequest(Long cityOrPlaceId, String name, BigDecimal latitude, BigDecimal longitude,
                                 List<CreatePlaceRequest.PhotoRequest> photos, List<CreatePlaceRequest.ContactRequest> contacts,
                                 List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        if (cityOrPlaceId == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("name is required and must be at most 255 characters");
        }
        if (latitude == null || latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude == null || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (photos != null) {
            for (var photo : photos) {
                if (photo == null || photo.url() == null || photo.url().isBlank() || photo.position() == null || photo.position() < 0) {
                    throw new IllegalArgumentException("photo url and non-negative position are required");
                }
            }
        }
        if (contacts != null) {
            for (var contact : contacts) {
                if (contact == null || contact.type() == null || contact.value() == null || contact.value().isBlank()) {
                    throw new IllegalArgumentException("contact type and value are required");
                }
            }
        }
        validateOpeningHours(openingHours);
    }

    private void validateOpeningHours(List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        if (openingHours == null) {
            return;
        }
        for (var hours : openingHours) {
            if (hours == null || hours.dayOfWeek() == null) {
                throw new IllegalArgumentException("dayOfWeek is required");
            }
            if (hours.closed()) {
                if (hours.openingTime() != null || hours.closingTime() != null) {
                    throw new IllegalArgumentException("Closed opening hours must not contain times");
                }
            } else {
                if (hours.openingTime() == null || hours.closingTime() == null || !hours.openingTime().isBefore(hours.closingTime())) {
                    throw new IllegalArgumentException("Opening time must be before closing time for an open interval");
                }
            }
        }
    }
}
