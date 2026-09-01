package com.onmyway;

import com.onmyway.data.entities.*;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OnMyWayApplicationTests {

    private final CityRepository cityRepository;
    private final PlaceRepository placeRepository;

    OnMyWayApplicationTests(CityRepository cityRepository, PlaceRepository placeRepository) {
        this.cityRepository = cityRepository;
        this.placeRepository = placeRepository;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void savesPlaceWithCityAndPersistsCoreFields() {
        City city = new City();
        city.setName("Amsterdam");
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.367600"));
        city.setLongitude(new BigDecimal("4.904100"));
        city.setTimezone("Europe/Amsterdam");
        city = cityRepository.save(city);

        Place place = new Place();
        place.setCity(city);
        place.setName("Rijksmuseum");
        place.setDescription("Museum");
        place.setLatitude(new BigDecimal("52.360000"));
        place.setLongitude(new BigDecimal("4.885200"));
        place.setStatus(PlaceStatus.PUBLISHED);

        Place saved = placeRepository.saveAndFlush(place);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCity().getId()).isEqualTo(city.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void allowsMultipleOpeningIntervalsForSameDay() {
        Place place = new Place();
        City city = new City();
        city.setName("Test city");
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.000000"));
        city.setLongitude(new BigDecimal("4.000000"));
        city.setTimezone("Europe/Amsterdam");
        city = cityRepository.save(city);
        place.setCity(city);
        place.setName("Test place");
        place.setLatitude(new BigDecimal("52.000001"));
        place.setLongitude(new BigDecimal("4.000001"));
        place = placeRepository.save(place);

        PlaceOpeningHours morning = openingHours(place, LocalTime.of(9, 0), LocalTime.of(12, 0));
        PlaceOpeningHours evening = openingHours(place, LocalTime.of(14, 0), LocalTime.of(18, 0));

        assertThat(morning.getDayOfWeek()).isEqualTo(evening.getDayOfWeek());
        assertThat(morning.getOpeningTime()).isBefore(morning.getClosingTime());
        assertThat(evening.getOpeningTime()).isBefore(evening.getClosingTime());
    }

    private PlaceOpeningHours openingHours(Place place, LocalTime opening, LocalTime closing) {
        PlaceOpeningHours hours = new PlaceOpeningHours();
        hours.setPlace(place);
        hours.setDayOfWeek(DayOfWeek.MONDAY);
        hours.setOpeningTime(opening);
        hours.setClosingTime(closing);
        hours.setClosed(false);
        return hours;
    }
}
