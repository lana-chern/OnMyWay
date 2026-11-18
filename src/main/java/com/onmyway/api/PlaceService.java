package com.onmyway.api;

import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import com.onmyway.api.exception.ResourceNotFoundException;
import com.onmyway.api.exception.StalePlaceVersionException;
import com.onmyway.data.entities.*;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceRepository;
import com.onmyway.data.repositories.PlaceRevisionRepository;
import com.onmyway.data.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class PlaceService {
    private static final int MAX_REJECTION_REASON_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<PlaceRevisionStatus> WORKING_STATUSES =
            Set.of(PlaceRevisionStatus.DRAFT, PlaceRevisionStatus.PENDING_MODERATION, PlaceRevisionStatus.REJECTED);

    private final PlaceRepository placeRepository;
    private final PlaceRevisionRepository revisionRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;

    public PlaceService(PlaceRepository placeRepository, PlaceRevisionRepository revisionRepository,
                        CityRepository cityRepository, UserRepository userRepository) {
        this.placeRepository = placeRepository;
        this.revisionRepository = revisionRepository;
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<PlaceResponse> findPublished(Long cityId, String query, int page, int size) {
        if (cityId == null) throw new IllegalArgumentException("cityId is required");
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and %d".formatted(MAX_PAGE_SIZE));
        }
        if (!cityRepository.existsById(cityId)) {
            throw new ResourceNotFoundException("City %d not found".formatted(cityId));
        }
        String normalizedQuery = query == null || query.isBlank() ? null : escapeLikeWildcards(query.strip());
        return placeRepository.searchPublished(cityId, normalizedQuery, PageRequest.of(page, size))
                .map(place -> PlaceResponse.from(place, place.getPublishedRevision()));
    }

    private String escapeLikeWildcards(String query) {
        return query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Transactional(readOnly = true)
    public PlaceResponse findById(Long id) {
        Place place = placeRepository.findById(id)
                .filter(candidate -> candidate.getStatus() != PlaceStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        PlaceRevision published = place.getPublishedRevision();
        if (published == null || published.getStatus() != PlaceRevisionStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Place %d not found".formatted(id));
        }
        return PlaceResponse.from(place, published);
    }

    @Transactional(readOnly = true)
    public PlaceResponse findForManagement(Long id, Long actorId, boolean canManageAnyPlace) {
        Place place = placeRepository.findById(id)
                .filter(candidate -> candidate.getStatus() != PlaceStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, canManageAnyPlace);
        PlaceRevision revision = findWorkingRevision(place.getId()).orElseGet(place::getPublishedRevision);
        if (revision == null) {
            throw new ResourceNotFoundException("Revision for place %d not found".formatted(id));
        }
        return PlaceResponse.from(place, revision);
    }

    public PlaceResponse create(CreatePlaceRequest request, Long ownerId) {
        validateRequest(request.cityId(), request.name(), request.latitude(), request.longitude(),
                request.photos(), request.contacts(), request.openingHours());
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City %d not found".formatted(request.cityId())));
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Place place = new Place();
        place.setCity(city);
        place.setOwner(owner);
        place.setName(request.name());
        place.setDescription(request.description());
        place.setLatitude(request.latitude());
        place.setLongitude(request.longitude());
        place.setStatus(PlaceStatus.DRAFT);
        placeRepository.saveAndFlush(place);

        PlaceRevision revision = new PlaceRevision();
        revision.setPlace(place);
        revision.setStatus(PlaceRevisionStatus.DRAFT);
        apply(revision, request.name(), request.description(), request.latitude(), request.longitude(),
                request.photos(), request.contacts(), request.openingHours());
        revisionRepository.saveAndFlush(revision);
        return PlaceResponse.from(place, revision);
    }

    public PlaceResponse update(Long id, UpdatePlaceRequest request, Long actorId, boolean canManageAnyPlace) {
        validateRequest(id, request.name(), request.latitude(), request.longitude(),
                request.photos(), request.contacts(), request.openingHours());
        Place place = placeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, canManageAnyPlace);
        if (place.getStatus() == PlaceStatus.ARCHIVED) {
            throw new ResourceNotFoundException("Place %d not found".formatted(id));
        }

        PlaceRevision revision = findWorkingRevision(id).orElseGet(() -> createWorkingCopy(place));
        requireCurrentVersion(request.version(), revision.getVersion());
        apply(revision, request.name(), request.description(), request.latitude(), request.longitude(),
                request.photos(), request.contacts(), request.openingHours());
        syncLegacyScalars(place, revision);
        revisionRepository.flush();
        return PlaceResponse.from(place, revision);
    }

    public void delete(Long id, Long actorId, boolean admin) {
        Place place = placeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, admin);
        place.setStatus(PlaceStatus.ARCHIVED);
        findWorkingRevision(id).ifPresent(revision -> revision.setStatus(PlaceRevisionStatus.ARCHIVED));
    }

    public PlaceResponse submitForModeration(Long id, Long actorId) {
        Place place = placeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        assertCanManage(place, actorId, false);
        if (place.getStatus() == PlaceStatus.ARCHIVED) {
            throw new ResourceNotFoundException("Place %d not found".formatted(id));
        }
        PlaceRevision revision = findWorkingRevision(id)
                .orElseThrow(() -> new IllegalStateException("Place has no working revision"));
        if (revision.getStatus() != PlaceRevisionStatus.DRAFT && revision.getStatus() != PlaceRevisionStatus.REJECTED) {
            throw new IllegalStateException("Only draft or rejected revisions can be submitted for moderation");
        }
        revision.setRejectionReason(null);
        place.setRejectionReason(null);
        revision.setStatus(PlaceRevisionStatus.PENDING_MODERATION);
        if (place.getPublishedRevision() == null) place.setStatus(PlaceStatus.PENDING_MODERATION);
        revisionRepository.flush();
        return PlaceResponse.from(place, revision);
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> findPendingModeration(boolean moderatorOrAdmin) {
        assertCanModerate(moderatorOrAdmin);
        return revisionRepository.findByStatusOrderByCreatedAtAsc(PlaceRevisionStatus.PENDING_MODERATION)
                .stream().map(revision -> PlaceResponse.from(revision.getPlace(), revision)).toList();
    }

    public PlaceResponse publish(Long id, boolean moderatorOrAdmin, Long expectedVersion) {
        return moderatePendingRevision(id, moderatorOrAdmin, PlaceRevisionStatus.PUBLISHED, null, expectedVersion);
    }

    public PlaceResponse reject(Long id, boolean moderatorOrAdmin, Long expectedVersion, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A rejection reason is required");
        String normalizedReason = reason.strip();
        if (normalizedReason.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new IllegalArgumentException("Rejection reason must be at most %d characters".formatted(MAX_REJECTION_REASON_LENGTH));
        }
        return moderatePendingRevision(id, moderatorOrAdmin, PlaceRevisionStatus.REJECTED,
                normalizedReason, expectedVersion);
    }

    private PlaceResponse moderatePendingRevision(Long id, boolean moderatorOrAdmin, PlaceRevisionStatus targetStatus,
                                                   String rejectionReason, Long expectedVersion) {
        assertCanModerate(moderatorOrAdmin);
        if (expectedVersion == null) throw new IllegalArgumentException("version is required");
        Place place = placeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place %d not found".formatted(id)));
        if (place.getStatus() == PlaceStatus.ARCHIVED) {
            throw new ResourceNotFoundException("Place %d not found".formatted(id));
        }
        PlaceRevision candidate = revisionRepository.findByPlaceIdAndStatus(id, PlaceRevisionStatus.PENDING_MODERATION)
                .orElseThrow(() -> new IllegalStateException("Only places with a pending revision can be moderated"));
        requireCurrentVersion(expectedVersion, candidate.getVersion());

        if (targetStatus == PlaceRevisionStatus.PUBLISHED) {
            PlaceRevision previous = place.getPublishedRevision();
            if (previous != null && !Objects.equals(previous.getId(), candidate.getId())) {
                previous.setStatus(PlaceRevisionStatus.SUPERSEDED);
                // Free the partial unique index before promoting the candidate; both updates
                // remain inside the same transaction and are committed atomically.
                revisionRepository.flush();
            }
            candidate.setRejectionReason(null);
            place.setRejectionReason(null);
            candidate.setStatus(PlaceRevisionStatus.PUBLISHED);
            place.setPublishedRevision(candidate);
            place.setStatus(PlaceStatus.PUBLISHED);
            syncLegacyScalars(place, candidate);
        } else {
            candidate.setRejectionReason(rejectionReason);
            place.setRejectionReason(rejectionReason);
            candidate.setStatus(PlaceRevisionStatus.REJECTED);
            place.setStatus(place.getPublishedRevision() == null ? PlaceStatus.REJECTED : PlaceStatus.PUBLISHED);
        }
        revisionRepository.flush();
        placeRepository.flush();
        return PlaceResponse.from(place, candidate);
    }

    private PlaceRevision createWorkingCopy(Place place) {
        PlaceRevision source = place.getPublishedRevision();
        if (source == null || source.getStatus() != PlaceRevisionStatus.PUBLISHED) {
            throw new IllegalStateException("Place has no editable revision");
        }
        PlaceRevision copy = new PlaceRevision();
        copy.setPlace(place);
        copy.setStatus(PlaceRevisionStatus.DRAFT);
        // Keep the API version monotonic across revision generations so stale requests
        // from a just-published revision cannot accidentally edit its successor.
        copy.setVersion(source.getVersion() + 1);
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setLatitude(source.getLatitude());
        copy.setLongitude(source.getLongitude());
        source.getPhotos().forEach(photo -> {
            PlaceRevisionPhoto clone = new PlaceRevisionPhoto();
            clone.setRevision(copy);
            clone.setUrl(photo.getUrl());
            clone.setPosition(photo.getPosition());
            clone.setDescription(photo.getDescription());
            copy.getPhotos().add(clone);
        });
        source.getContacts().forEach(contact -> {
            PlaceRevisionContact clone = new PlaceRevisionContact();
            clone.setRevision(copy);
            clone.setType(contact.getType());
            clone.setValue(contact.getValue());
            copy.getContacts().add(clone);
        });
        source.getOpeningHours().forEach(hours -> {
            PlaceRevisionOpeningHours clone = new PlaceRevisionOpeningHours();
            clone.setRevision(copy);
            clone.setDayOfWeek(hours.getDayOfWeek());
            clone.setOpeningTime(hours.getOpeningTime());
            clone.setClosingTime(hours.getClosingTime());
            clone.setClosed(hours.isClosed());
            copy.getOpeningHours().add(clone);
        });
        return revisionRepository.save(copy);
    }

    private java.util.Optional<PlaceRevision> findWorkingRevision(Long placeId) {
        return revisionRepository.findFirstByPlaceIdAndStatusInOrderByCreatedAtDesc(placeId, WORKING_STATUSES);
    }

    private void requireCurrentVersion(Long expected, Long actual) {
        if (expected == null) throw new IllegalArgumentException("version is required");
        if (!Objects.equals(expected, actual)) throw new StalePlaceVersionException(expected, actual);
    }

    private void assertCanModerate(boolean moderatorOrAdmin) {
        if (!moderatorOrAdmin) throw new AccessDeniedException("Moderator or administrator role is required");
    }

    private void assertCanManage(Place place, Long actorId, boolean canManageAnyPlace) {
        if (!canManageAnyPlace && (place.getOwner() == null || !Objects.equals(place.getOwner().getId(), actorId))) {
            throw new AccessDeniedException("You can manage only your own places");
        }
    }

    private void syncLegacyScalars(Place place, PlaceRevision revision) {
        place.setName(revision.getName());
        place.setDescription(revision.getDescription());
        place.setLatitude(revision.getLatitude());
        place.setLongitude(revision.getLongitude());
    }

    private void apply(PlaceRevision revision, String name, String description, BigDecimal latitude, BigDecimal longitude,
                       List<CreatePlaceRequest.PhotoRequest> photos, List<CreatePlaceRequest.ContactRequest> contacts,
                       List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        revision.setName(name);
        revision.setDescription(description);
        revision.setLatitude(latitude);
        revision.setLongitude(longitude);
        // Child collection replacements must advance the revision's @Version as well.
        revision.setUpdatedAt(java.time.Instant.now());
        revision.getPhotos().clear();
        if (photos != null) for (var request : photos) {
            PlaceRevisionPhoto photo = new PlaceRevisionPhoto();
            photo.setRevision(revision); photo.setUrl(request.url()); photo.setPosition(request.position());
            photo.setDescription(request.description()); revision.getPhotos().add(photo);
        }
        revision.getContacts().clear();
        if (contacts != null) for (var request : contacts) {
            PlaceRevisionContact contact = new PlaceRevisionContact();
            contact.setRevision(revision); contact.setType(request.type()); contact.setValue(request.value());
            revision.getContacts().add(contact);
        }
        revision.getOpeningHours().clear();
        if (openingHours != null) for (var request : openingHours) {
            PlaceRevisionOpeningHours hours = new PlaceRevisionOpeningHours();
            hours.setRevision(revision); hours.setDayOfWeek(request.dayOfWeek());
            hours.setOpeningTime(request.openingTime()); hours.setClosingTime(request.closingTime());
            hours.setClosed(request.closed()); revision.getOpeningHours().add(hours);
        }
    }

    private void validateRequest(Long cityOrPlaceId, String name, BigDecimal latitude, BigDecimal longitude,
                                 List<CreatePlaceRequest.PhotoRequest> photos, List<CreatePlaceRequest.ContactRequest> contacts,
                                 List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        if (cityOrPlaceId == null) throw new IllegalArgumentException("id is required");
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("name is required and must be at most 255 characters");
        }
        if (latitude == null || latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude == null || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (photos != null) for (var photo : photos) {
            if (photo == null || photo.url() == null || photo.url().isBlank() || photo.position() == null || photo.position() < 0) {
                throw new IllegalArgumentException("photo url and non-negative position are required");
            }
        }
        if (contacts != null) for (var contact : contacts) {
            if (contact == null || contact.type() == null || contact.value() == null || contact.value().isBlank()) {
                throw new IllegalArgumentException("contact type and value are required");
            }
        }
        validateOpeningHours(openingHours);
    }

    private void validateOpeningHours(List<CreatePlaceRequest.OpeningHoursRequest> openingHours) {
        if (openingHours == null) return;
        for (var hours : openingHours) {
            if (hours == null || hours.dayOfWeek() == null) throw new IllegalArgumentException("dayOfWeek is required");
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
