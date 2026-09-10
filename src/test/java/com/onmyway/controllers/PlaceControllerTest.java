package com.onmyway.controllers;

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

import java.math.BigDecimal;

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

        mockMvc.perform(post("/api/places")
                        .with(user(owner.getEmail()).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/places/[0-9]+")))
                .andExpect(jsonPath("$.name").value("Rijksmuseum"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-validation@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Validation"));
        String body = String.format(
                "{\"cityId\":%d,\"name\":\"Bad\",\"latitude\":100,\"longitude\":4.9}",
                city.getId());

        mockMvc.perform(post("/api/places")
                        .with(user(owner.getEmail()).roles("ORGANIZER"))
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
                        .with(user(owner.getEmail()).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\",\"description\":\"Updated description\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.latitude").value(52.370000))
                .andExpect(jsonPath("$.longitude").value(4.900000));
    }

    @Test
    void rejectsPlaceUpdateForAnotherOrganizer() throws Exception {
        User owner = userRepository.saveAndFlush(testUser("organizer-owner@example.com", Role.ORGANIZER));
        User anotherOrganizer = userRepository.saveAndFlush(testUser("organizer-another@example.com", Role.ORGANIZER));
        City city = cityRepository.saveAndFlush(city("Amsterdam Forbidden Update"));
        Place place = placeRepository.saveAndFlush(place(city, owner, "Existing", PlaceStatus.DRAFT));

        mockMvc.perform(put("/api/places/{id}", place.getId())
                        .with(user(anotherOrganizer.getEmail()).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New name\",\"latitude\":52.370000,\"longitude\":4.900000}"))
                .andExpect(status().isForbidden());
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
