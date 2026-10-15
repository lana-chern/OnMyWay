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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
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
                .andExpect(jsonPath("$.status").value("PENDING_MODERATION"));

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
        placeRepository.saveAndFlush(place(owner, "Draft", PlaceStatus.DRAFT));
        placeRepository.saveAndFlush(place(owner, "Published", PlaceStatus.PUBLISHED));

        mockMvc.perform(get("/api/moderation/places/pending").with(user(principal(moderator))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(pending.getId().intValue())));
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
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

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
                        .content("{\"name\":\"Edited by moderator\",\"latitude\":52.370000,\"longitude\":4.900000}"))
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
