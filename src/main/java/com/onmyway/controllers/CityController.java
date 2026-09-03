package com.onmyway.controllers;

import com.onmyway.api.CityService;
import com.onmyway.api.dto.CityResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
public class CityController {
    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    public List<CityResponse> findAll() {
        return cityService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CityResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(cityService.findById(id));
    }
}
