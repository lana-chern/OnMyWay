package com.onmyway.data.repositories;

import com.onmyway.data.entities.City;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CityRepositoryTest {

    @Autowired
    private CityRepository cityRepository;

    @Test
    void savesCity() {
        City city = new City();
        city.setName("Amsterdam");
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.367600"));
        city.setLongitude(new BigDecimal("4.904100"));
        city.setTimezone("Europe/Amsterdam");

        City saved = cityRepository.saveAndFlush(city);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Amsterdam");
        assertThat(saved.getCountryCode()).isEqualTo("NL");
    }
}
