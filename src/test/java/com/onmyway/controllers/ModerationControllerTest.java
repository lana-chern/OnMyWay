package com.onmyway.controllers;

import com.onmyway.auth.OnMyWayUserDetails;
import com.onmyway.data.entities.City;
import com.onmyway.data.entities.Place;
import com.onmyway.data.entities.PlaceStatus;
import com.onmyway.data.entities.Role;
import com.onmyway.data.entities.User;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceRepository;
import com.onmyway.data.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class ModerationControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CityRepository cityRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired UserRepository userRepository;

    @Test
    void ownerCanSubmitDraftForModeration() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-submit-owner@example.com", Role.ORGANIZER));
        Place place = placeRepository.saveAndFlush(place(owner, "Draft", PlaceStatus.DRAFT));

        mockMvc.perform(post("/api/places/{id}/submit-for-moderation", place.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_MODERATION"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus())
                .isEqualTo(PlaceStatus.PENDING_MODERATION);
    }

    @Test
    void anotherOrganizerCannotSubmitPlaceForModeration() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-submit-owner-2@example.com", Role.ORGANIZER));
        User anotherOrganizer = userRepository.saveAndFlush(testUser("moderation-submit-other@example.com", Role.ORGANIZER));
        Place place = placeRepository.saveAndFlush(place(owner, "Owner draft", PlaceStatus.DRAFT));

        mockMvc.perform(post("/api/places/{id}/submit-for-moderation", place.getId())
                        .with(user(principal(anotherOrganizer))))
                .andExpect(status().isForbidden());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.DRAFT);
    }

    @Test
    void moderatorListsOnlyPlacesPendingModeration() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-list-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-list-moderator@example.com", Role.MODERATOR));
        Place pending = placeRepository.saveAndFlush(place(owner, "Pending", PlaceStatus.PENDING_MODERATION));
        Place draft = placeRepository.saveAndFlush(place(owner, "Draft", PlaceStatus.DRAFT));
        Place published = placeRepository.saveAndFlush(place(owner, "Published", PlaceStatus.PUBLISHED));

        mockMvc.perform(get("/api/moderation/places/pending").with(user(principal(moderator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(pending.getId().intValue())))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(draft.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(published.getId().intValue()))))
                .andExpect(jsonPath("$[*].status").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING_MODERATION"))));
    }

    @Test
    void administratorCanAccessModerationQueue() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-admin-queue-owner@example.com", Role.ORGANIZER));
        User admin = userRepository.saveAndFlush(testUser("moderation-admin-queue@example.com", Role.ADMIN));
        Place pending = placeRepository.saveAndFlush(place(owner, "Admin queue pending", PlaceStatus.PENDING_MODERATION));

        mockMvc.perform(get("/api/moderation/places/pending").with(user(principal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(pending.getId().intValue())));
    }

    @Test
    void moderatorCanPublishPendingPlace() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-publish-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-publish-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));

        mockMvc.perform(post("/api/moderation/places/{id}/publish", place.getId())
                        .with(user(principal(moderator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
    }

    @Test
    void administratorCanPublishPendingPlace() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-admin-publish-owner@example.com", Role.ORGANIZER));
        User admin = userRepository.saveAndFlush(testUser("moderation-admin-publish@example.com", Role.ADMIN));
        Place place = placeRepository.saveAndFlush(place(owner, "Admin pending place", PlaceStatus.PENDING_MODERATION));

        mockMvc.perform(post("/api/moderation/places/{id}/publish", place.getId())
                        .with(user(principal(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
    }

    @Test
    void moderatorCanRejectPendingPlaceWithReasonAndOwnerCanReadItAndResubmit() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-reject-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-reject-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));

        mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Missing opening hours\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Missing opening hours"));

        mockMvc.perform(get("/api/places/{id}/manage", place.getId()).with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Missing opening hours"));

        mockMvc.perform(post("/api/places/{id}/submit-for-moderation", place.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_MODERATION"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()));

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getRejectionReason()).isNull();
    }

    @Test
    void rejectionReasonIsRequired() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-empty-reason-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-empty-reason-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));

        mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus())
                .isEqualTo(PlaceStatus.PENDING_MODERATION);
    }

    @Test
    void missingOrNullRejectionReasonIsRejected() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-missing-reason-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-missing-reason-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));

        mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":null}"))
                .andExpect(status().isBadRequest());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus())
                .isEqualTo(PlaceStatus.PENDING_MODERATION);
        assertThat(placeRepository.findById(place.getId()).orElseThrow().getRejectionReason()).isNull();
    }

    @Test
    void rejectionReasonMustNotExceedOneThousandCharactersAfterTrimming() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-long-reason-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-long-reason-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));
        String tooLongReason = "x".repeat(1001);

        mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + tooLongReason + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus())
                .isEqualTo(PlaceStatus.PENDING_MODERATION);
        assertThat(placeRepository.findById(place.getId()).orElseThrow().getRejectionReason()).isNull();
    }

    @Test
    void strippedUnicodeWhitespaceAroundMaximumLengthReasonIsAccepted() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-boundary-reason-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-boundary-reason-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));
        String maxLengthReason = "x".repeat(1000);
        String reasonWithSurroundingWhitespace = "\u2003" + maxLengthReason + "\u2003";

        mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reasonWithSurroundingWhitespace + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(maxLengthReason));

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getRejectionReason())
                .isEqualTo(maxLengthReason);
    }

    @Test
    void anotherOrganizerCannotReadRejectedPlaceManagementDetails() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-manage-owner@example.com", Role.ORGANIZER));
        User anotherOrganizer = userRepository.saveAndFlush(testUser("moderation-manage-other@example.com", Role.ORGANIZER));
        Place place = placeRepository.saveAndFlush(place(owner, "Rejected place", PlaceStatus.REJECTED));
        place.setRejectionReason("Add accurate opening hours");
        placeRepository.saveAndFlush(place);

        mockMvc.perform(get("/api/places/{id}/manage", place.getId()).with(user(principal(anotherOrganizer))))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorPublishingPlaceClearsOldRejectionReason() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-publish-reason-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-publish-reason-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Pending place", PlaceStatus.PENDING_MODERATION));
        place.setRejectionReason("Old rejection reason");
        placeRepository.saveAndFlush(place);

        mockMvc.perform(post("/api/moderation/places/{id}/publish", place.getId())
                        .with(user(principal(moderator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue()));

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getRejectionReason()).isNull();
    }

    @Test
    void organizerCannotAccessModerationQueue() throws Exception {
        User organizer = userRepository.saveAndFlush(testUser("moderation-forbidden-organizer@example.com", Role.ORGANIZER));

        mockMvc.perform(get("/api/moderation/places/pending").with(user(principal(organizer))))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorCanEditAnotherOrganizersPlaceButCannotArchiveIt() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-edit-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-edit-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Original", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(moderator)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Edited by moderator\",\"latitude\":52.370000,\"longitude\":4.900000,\"version\":" + place.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edited by moderator"));

        mockMvc.perform(delete("/api/places/{id}", place.getId()).with(user(principal(moderator))))
                .andExpect(status().isForbidden());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.DRAFT);
    }

    @Test
    void cannotPublishPlaceThatIsNotPendingModeration() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-state-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-state-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Draft place", PlaceStatus.DRAFT));

        mockMvc.perform(post("/api/moderation/places/{id}/publish", place.getId())
                        .with(user(principal(moderator))))
                .andExpect(status().isConflict());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.DRAFT);
    }

    @Test
    void cannotPublishAlreadyPublishedPlaceAgain() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-republish-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-republish-mod@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Already published", PlaceStatus.PUBLISHED));

        mockMvc.perform(post("/api/moderation/places/{id}/publish", place.getId())
                        .with(user(principal(moderator))))
                .andExpect(status().isConflict());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
    }

    @Test
    void stalePlaceUpdateIsRejectedByOptimisticLocking() {
        User owner = userRepository.saveAndFlush(testUser("moderation-version-owner@example.com", Role.ORGANIZER));
        Place place = placeRepository.saveAndFlush(place(owner, "Original", PlaceStatus.DRAFT));

        Place staleCopy = placeRepository.findById(place.getId()).orElseThrow();
        Place currentCopy = placeRepository.findById(place.getId()).orElseThrow();
        currentCopy.setName("Updated by first request");
        placeRepository.saveAndFlush(currentCopy);

        staleCopy.setName("Stale update");
        assertThatThrownBy(() -> placeRepository.saveAndFlush(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getName())
                .isEqualTo("Updated by first request");
    }


    @Test
    void competingModerationDecisionsCannotOverwriteEachOther() {
        User owner = userRepository.saveAndFlush(testUser("moderation-race-owner@example.com", Role.ORGANIZER));
        Place place = placeRepository.saveAndFlush(place(owner, "Competing moderation", PlaceStatus.PENDING_MODERATION));

        Place publishRequest = placeRepository.findById(place.getId()).orElseThrow();
        Place rejectRequest = placeRepository.findById(place.getId()).orElseThrow();

        publishRequest.setStatus(PlaceStatus.PUBLISHED);
        publishRequest.setRejectionReason(null);
        placeRepository.saveAndFlush(publishRequest);

        rejectRequest.setStatus(PlaceStatus.REJECTED);
        rejectRequest.setRejectionReason("Rejected by stale moderation request");
        assertThatThrownBy(() -> placeRepository.saveAndFlush(rejectRequest))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Place persisted = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
        assertThat(persisted.getRejectionReason()).isNull();
    }


    @Test
    void concurrentPublishAndRejectRequestsReturnOneSuccessAndOneConflict() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("moderation-http-race-owner@example.com", Role.ORGANIZER));
        User moderator = userRepository.saveAndFlush(testUser("moderation-http-race-mod@example.com", Role.MODERATOR));
        User secondModerator = userRepository.saveAndFlush(testUser("moderation-http-race-mod-2@example.com", Role.MODERATOR));
        Place place = placeRepository.saveAndFlush(place(owner, "Concurrent moderation", PlaceStatus.PENDING_MODERATION));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> publishStatus = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start publish request");
                }
                return mockMvc.perform(post("/api/moderation/places/{id}/publish", place.getId())
                                .with(user(principal(moderator))))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> rejectStatus = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start reject request");
                }
                return mockMvc.perform(post("/api/moderation/places/{id}/reject", place.getId())
                                .with(user(principal(secondModerator)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Concurrent rejection\"}"))
                        .andReturn().getResponse().getStatus();
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int publishResponse = publishStatus.get(15, TimeUnit.SECONDS);
            int rejectResponse = rejectStatus.get(15, TimeUnit.SECONDS);
            assertThat(java.util.List.of(publishResponse, rejectResponse))
                    .containsExactlyInAnyOrder(200, 409);

            Place persisted = placeRepository.findById(place.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isIn(PlaceStatus.PUBLISHED, PlaceStatus.REJECTED);
            if (persisted.getStatus() == PlaceStatus.PUBLISHED) {
                assertThat(persisted.getRejectionReason()).isNull();
            } else {
                assertThat(persisted.getRejectionReason()).isEqualTo("Concurrent rejection");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private OnMyWayUserDetails principal(User user) {
        return new OnMyWayUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList(),
                true
        );
    }

    private User testUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setDisplayName("Test user");
        user.getRoles().add(role);
        return user;
    }

    private Place place(User owner, String name, PlaceStatus status) {
        City city = cityRepository.saveAndFlush(city("Moderation city " + name + " " + owner.getEmail()));
        Place place = new Place();
        place.setCity(city);
        place.setOwner(owner);
        place.setName(name);
        place.setStatus(status);
        place.setLatitude(new BigDecimal("52.360000"));
        place.setLongitude(new BigDecimal("4.885200"));
        return place;
    }

    private City city(String name) {
        City city = new City();
        city.setName(name);
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.367600"));
        city.setLongitude(new BigDecimal("4.904100"));
        city.setTimezone("Europe/Amsterdam");
        return city;
    }
}
