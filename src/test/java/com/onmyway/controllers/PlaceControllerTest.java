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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class PlaceControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CityRepository cityRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired UserRepository userRepository;

    @Test
    void createsPlace() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-create@example.com", Role.ORGANIZER));
        City city = city("Amsterdam Create");
        String body = String.format(
                "{\"cityId\":%d,\"name\":\"Rijksmuseum\",\"description\":\"Museum\",\"latitude\":52.360000,\"longitude\":4.885200," +
                "\"photos\":[{\"url\":\"https://example.com/photo.jpg\",\"position\":0}]," +
                "\"contacts\":[{\"type\":\"WEBSITE\",\"value\":\"https://example.com\"}]," +
                "\"openingHours\":[{\"dayOfWeek\":\"MONDAY\",\"openingTime\":\"09:00\",\"closingTime\":\"18:00\",\"closed\":false}]}",
                cityRepository.saveAndFlush(city).getId());

        var result = mockMvc.perform(post("/api/places")
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/places/[0-9]+")))
                .andExpect(jsonPath("$.name").value("Rijksmuseum"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        Long placeId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        Place createdPlace = placeRepository.findById(placeId).orElseThrow();
        assertThat(createdPlace.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-validation@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Validation"));
        String body = String.format(
                "{\"cityId\":%d,\"name\":\"Bad\",\"latitude\":100,\"longitude\":4.9}",
                city.getId());

        mockMvc.perform(post("/api/places")
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsPlaceCreationForRegularUser() throws Exception {
        mockMvc.perform(post("/api/places")
                        .with(user("user@example.com").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesPlaceForOwner() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-update@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Update"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Old name", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"New name\",\"description\":\"Updated description\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.latitude").value(52.370000))
                .andExpect(jsonPath("$.longitude").value(4.900000));
    }

    @Test
    void rejectsPlaceUpdateWithStaleVersionAndPreservesCurrentData() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-stale-version@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Stale Version"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Current name", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":-1,\"name\":\"Stale update\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));

        Place unchanged = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Current name");
        assertThat(unchanged.getVersion()).isEqualTo(0L);
    }

    @Test
    void collectionOnlyUpdateIncrementsVersionAndRejectsStaleCollectionUpdate() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-collection-version@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Collection Version"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Unchanged name", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"version\\":0,\\"name\\":\\"Unchanged name\\",\\"latitude\\":52.360000,\\"longitude\\":4.885200," +
                                "\\"photos\\":[{\\"url\\":\\"https://example.com/first.jpg\\",\\"position\\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\\"version\\":0,\\"name\\":\\"Unchanged name\\",\\"latitude\\":52.360000,\\"longitude\\":4.885200," +
                                "\\"photos\\":[{\\"url\\":\\"https://example.com/stale.jpg\\",\\"position\\":0}]}"))
                .andExpect(status().isConflict());

        Place persisted = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(persisted.getVersion()).isEqualTo(1L);
        assertThat(persisted.getName()).isEqualTo("Unchanged name");
        assertThat(persisted.getPhotos()).extracting("url")
                .containsExactly("https://example.com/first.jpg");
    }

    @Test
    void rejectsPlaceUpdateWithoutVersion() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-missing-version@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Missing Version"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Current name", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Update without version\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isBadRequest());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getName()).isEqualTo("Current name");
    }

    @Test
    void rejectsPlaceUpdateForAnotherOrganizer() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-owner@example.com", Role.ORGANIZER));
        User anotherOrganizer = userRepository.saveAndFlush(testUser("organizer-another@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Forbidden Update"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Existing", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(anotherOrganizer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"New name\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminToUpdateAnotherOrganizersPlace() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-admin-owner@example.com", Role.ORGANIZER));
        User admin = userRepository.saveAndFlush(testUser("admin-update@example.com", Role.ADMIN));
        City city = cityRepository.saveAndFlush(city("Amsterdam Admin Update"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Original", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Admin update\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin update"));
    }

    @Test
    void deletesPlaceByArchivingItAndHidesItFromPublicReads() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-delete@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Delete"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Published place", PlaceStatus.PUBLISHED));

        mockMvc.perform(delete("/api/places/{id}", place.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isNoContent());

        Place archivedPlace = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(archivedPlace.getStatus()).isEqualTo(PlaceStatus.ARCHIVED);

        mockMvc.perform(get("/api/places").param("cityId", city.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get("/api/places/{id}", place.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUpdateToArchivedPlace() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-archived-update@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Archived Update"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Archived place", PlaceStatus.ARCHIVED));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Attempted update\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isNotFound());

        Place unchangedPlace = placeRepository.findById(place.getId()).orElseThrow();
        assertThat(unchangedPlace.getStatus()).isEqualTo(PlaceStatus.ARCHIVED);
        assertThat(unchangedPlace.getName()).isEqualTo("Archived place");
    }

    @Test
    void rejectsPlaceDeletionForRegularUser() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-user-role-delete@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam User Role Delete"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Protected published place", PlaceStatus.PUBLISHED));

        mockMvc.perform(delete("/api/places/{id}", place.getId())
                        .with(user("regular-user@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
    }

    @Test
    void rejectsPlaceDeletionForAnotherOrganizer() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-delete-owner@example.com", Role.ORGANIZER));
        User anotherOrganizer = userRepository.saveAndFlush(testUser("organizer-delete-another@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Forbidden Delete"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Still published", PlaceStatus.PUBLISHED));

        mockMvc.perform(delete("/api/places/{id}", place.getId())
                        .with(user(principal(anotherOrganizer))))
                .andExpect(status().isForbidden());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
    }

    @Test
    void allowsAdminToDeleteAnotherOrganizersPlace() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-admin-delete-owner@example.com", Role.ORGANIZER));
        User admin = userRepository.saveAndFlush(testUser("admin-delete@example.com", Role.ADMIN));
        City city = cityRepository.saveAndFlush(city("Amsterdam Admin Delete"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Admin archived place", PlaceStatus.PUBLISHED));

        mockMvc.perform(delete("/api/places/{id}", place.getId())
                        .with(user(principal(admin))))
                .andExpect(status().isNoContent());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.ARCHIVED);
    }

    @Test
    void rejectsUnauthenticatedPlaceDeletion() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-unauth-delete@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Unauthenticated Delete"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Protected place", PlaceStatus.PUBLISHED));

        mockMvc.perform(delete("/api/places/{id}", place.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(placeRepository.findById(place.getId()).orElseThrow().getStatus()).isEqualTo(PlaceStatus.PUBLISHED);
    }

    @Test
    void authorizesPlaceUpdateByOwnerIdRatherThanEmail() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-id-owner@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Owner Id"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Original name", PlaceStatus.DRAFT));
        OnMyWayUserDetails principalWithDifferentEmail = new OnMyWayUserDetails(
                owner.getId(),
                "different-email@example.com",
                owner.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")),
                true
        );

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(principalWithDifferentEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Updated by id\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated by id"));
    }

    @Test
    void returnsOnlyPublishedPlacesForCity() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-published@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Published"));
        placeRepository.saveAndFlush(place(city, owner, "Draft", PlaceStatus.DRAFT));
        placeRepository.saveAndFlush(place(city, owner, "Published", PlaceStatus.PUBLISHED));

        mockMvc.perform(get("/api/places").param("cityId", city.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Published"));
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

    private City city(String name) {
        City city = new City();
        city.setName(name);
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.367600"));
        city.setLongitude(new BigDecimal("4.904100"));
        city.setTimezone("Europe/Amsterdam");
        return city;
    }

    private Place place(City city, User owner, String name, PlaceStatus status) {
        Place place = new Place();
        place.setCity(city);
        place.setOwner(owner);
        place.setName(name);
        place.setStatus(status);
        place.setLatitude(new BigDecimal("52.360000"));
        place.setLongitude(new BigDecimal("4.885200"));
        return place;
    }
}
