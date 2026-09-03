package com.onmyway.api.dto;

import com.onmyway.data.entities.City;

import java.math.BigDecimal;

public record CityResponse(Long id, String name, String countryCode, BigDecimal latitude,
                           BigDecimal longitude, String timezone) {
    public static CityResponse from(City city) {
        return new CityResponse(city.getId(), city.getName(), city.getCountryCode(),
                city.getLatitude(), city.getLongitude(), city.getTimezone());
    }
}
