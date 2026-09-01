package com.onmyway;

import com.onmyway.data.entities.*;
import com.onmyway.data.repositories.CityRepository;
import com.onmyway.data.repositories.PlaceOpeningHoursRepository;
import com.onmyway.data.repositories.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PlaceOpeningHoursRepository openingHoursRepository;

    @Autowired
    OnMyWayApplicationTests(
            CityRepository cityRepository,
            PlaceRepository placeRepository,
            PlaceOpeningHoursRepository openingHoursRepository
    ) {
        this.cityRepository = cityRepository;
        this.placeRepository = placeRepository;
        this.openingHoursRepository = openingHoursRepository;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void savesPlaceWithCityAndPersistsCoreFields() {
        City city = city("Amsterdam", "52.367600", "4.904100");

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
    void persistsPlaceChildrenThroughCascade() {
        City city = city("Amsterdam", "52.367600", "4.904100");
        Place place = place(city, "Rijksmuseum", "52.360000", "4.885200");

        PlacePhoto photo = new PlacePhoto();
        photo.setPlace(place);
        photo.setUrl("https://example.com/photo.jpg");
        photo.setPosition(1);
        place.getPhotos().add(photo);

        PlaceContact contact = new PlaceContact();
        contact.setPlace(place);
        contact.setType(PlaceContactType.WEBSITE);
        contact.setValue("https://example.com");
        place.getContacts().add(contact);

        PlaceOpeningHours hours = openingHours(place, LocalTime.of(9, 0), LocalTime.of(18, 0));
        place.getOpeningHours().add(hours);

        placeRepository.saveAndFlush(place);

        assertThat(photo.getId()).isNotNull();
        assertThat(contact.getId()).isNotNull();
        assertThat(hours.getId()).isNotNull();
        assertThat(openingHoursRepository.findAll()).hasSize(1);
    }

    @Test
    void persistsMultipleOpeningIntervalsForSameDay() {
        City city = city("Test city", "52.000000", "4.000000");
        Place place = place(city, "Test place", "52.000001", "4.000001");

        openingHoursRepository.saveAndFlush(openingHours(place, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        openingHoursRepository.saveAndFlush(openingHours(place, LocalTime.of(14, 0), LocalTime.of(18, 0)));

        assertThat(openingHoursRepository.findAll()).hasSize(2);
    }

    private City city(String name, String latitude, String longitude) {
        City city = new City();
        city.setName(name);
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal(latitude));
        city.setLongitude(new BigDecimal(longitude));
        city.setTimezone("Europe/Amsterdam");
        return cityRepository.saveAndFlush(city);
    }

    private Place place(City city, String name, String latitude, String longitude) {
        Place place = new Place();
        place.setCity(city);
        place.setName(name);
        place.setLatitude(new BigDecimal(latitude));
        place.setLongitude(new BigDecimal(longitude));
        return placeRepository.saveAndFlush(place);
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
