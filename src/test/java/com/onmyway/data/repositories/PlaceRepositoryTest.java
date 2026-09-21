package com.onmyway.data.repositories;

import com.onmyway.data.entities.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PlaceRepositoryTest {

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceOpeningHoursRepository openingHoursRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesPlaceWithCityAndCoreFields() {
        City city = city("Amsterdam");
        User owner = testUser("owner-core@example.com");
        Place place = place(city, owner, "Rijksmuseum");
        place.setDescription("Museum");
        place.setStatus(PlaceStatus.PUBLISHED);

        Place saved = placeRepository.saveAndFlush(place);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCity().getId()).isEqualTo(city.getId());
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void persistsPlaceChildrenThroughCascade() {
        City city = city("Amsterdam");
        User owner = testUser("owner-children@example.com");
        Place place = place(city, owner, "Rijksmuseum");

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
    }

    @Test
    void persistsMultipleOpeningIntervalsForSameDay() {
        City city = city("Test city");
        User owner = testUser("owner-hours@example.com");
        Place place = place(city, owner, "Test place");
        placeRepository.saveAndFlush(place);

        openingHoursRepository.saveAndFlush(openingHours(place, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        openingHoursRepository.saveAndFlush(openingHours(place, LocalTime.of(14, 0), LocalTime.of(18, 0)));

        assertThat(openingHoursRepository.findAll()).hasSize(2);
    }

    private User testUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-password-hash");
        user.setDisplayName("Test user");
        user.getRoles().add(Role.ORGANIZER);
        return userRepository.saveAndFlush(user);
    }

    private City city(String name) {
        City city = new City();
        city.setName(name);
        city.setCountryCode("NL");
        city.setLatitude(new BigDecimal("52.367600"));
        city.setLongitude(new BigDecimal("4.904100"));
        city.setTimezone("Europe/Amsterdam");
        return cityRepository.saveAndFlush(city);
    }

    private Place place(City city, User owner, String name) {
        Place place = new Place();
        place.setCity(city);
        place.setOwner(owner);
        place.setName(name);
        place.setLatitude(new BigDecimal("52.360000"));
        place.setLongitude(new BigDecimal("4.885200"));
        return place;
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
