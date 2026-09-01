package com.onmyway.controllers;

import com.onmyway.data.entities.City;
import com.onmyway.data.entities.Place;
import com.onmyway.data.entities.PlaceStatus;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlaceControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CityRepository cityRepository;
    @Autowired PlaceRepository placeRepository;

    @Test
    void createsPlace() throws Exception {
        City city = city("Amsterdam Create");
        String body = String.format(
                "{\"cityId\":%d,\"name\":\"Rijksmuseum\",\"description\":\"Museum\",\"latitude\":52.360000,\"longitude\":4.885200," +
                "\"photos\":[{\"url\":\"https://example.com/photo.jpg\",\"position\":0}]," +
                "\"contacts\":[{\"type\":\"WEBSITE\",\"value\":\"https://example.com\"}]," +
                "\"openingHours\":[{\"dayOfWeek\":\"MONDAY\",\"openingTime\":\"09:00\",\"closingTime\":\"18:00\",\"closed\":false}]}",
                cityRepository.saveAndFlush(city).getId());

        mockMvc.perform(post("/api/places").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/places/[0-9]+")))
                .andExpect(jsonPath("$.name").value("Rijksmuseum"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void rejectsInvalidCoordinates() throws Exception {
        City city = cityRepository.saveAndFlush(city("Amsterdam Validation"));
        String body = String.format(
                "{\"cityId\":%d,\"name\":\"Bad\",\"latitude\":100,\"longitude\":4.9}",
                city.getId());

        mockMvc.perform(post("/api/places").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void returnsOnlyPublishedPlacesForCity() throws Exception {
        City city = cityRepository.saveAndFlush(city("Amsterdam Published"));
        placeRepository.saveAndFlush(place(city, "Draft", PlaceStatus.DRAFT));
        placeRepository.saveAndFlush(place(city, "Published", PlaceStatus.PUBLISHED));

        mockMvc.perform(get("/api/places").param("cityId", city.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Published"));
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

    private Place place(City city, String name, PlaceStatus status) {
        Place place = new Place();
        place.setCity(city);
        place.setName(name);
        place.setStatus(status);
        place.setLatitude(new BigDecimal("52.360000"));
        place.setLongitude(new BigDecimal("4.885200"));
        return place;
    }
}
