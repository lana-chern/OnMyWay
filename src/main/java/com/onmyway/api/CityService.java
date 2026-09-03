package com.onmyway.api;

import com.onmyway.api.dto.CityResponse;
import com.onmyway.api.exception.ResourceNotFoundException;
import com.onmyway.data.repositories.CityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CityService {
    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    public List<CityResponse> findAll() {
        return cityRepository.findAll().stream().map(CityResponse::from).toList();
    }

    public CityResponse findById(Long id) {
        return cityRepository.findById(id)
                .map(CityResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("City %d not found".formatted(id)));
    }
}
