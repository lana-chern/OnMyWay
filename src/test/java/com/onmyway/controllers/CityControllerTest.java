package com.onmyway.controllers;

import com.onmyway.data.entities.City;
import com.onmyway.data.repositories.CityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CityControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired CityRepository cityRepository;

    @Test
    void getsCityById() throws Exception {
        City city = new City();
        city.setName("Amsterdam"); city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.367600")); city.setLongitude(new BigDecimal("4.904100"));
        city.setTimezone("Europe/Amsterdam");
        city = cityRepository.saveAndFlush(city);

        mockMvc.perform(get("/api/cities/{id}", city.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Amsterdam"))
                .andExpect(jsonPath("$.countryCode").value("NL"));
    }

    @Test
    void returns404ForUnknownCity() throws Exception {
        mockMvc.perform(get("/api/cities/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}
