package com.onmyway.controllers;

import com.onmyway.data.entities.District;
import com.onmyway.data.repositories.DistrictRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
public class MainController {
    private final DistrictRepository districtRepository;

    public MainController(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    @GetMapping("/hello")
    public String returnHelloWorld() {
        return "Hello, World!";
    }

    @GetMapping
    public List<District> getDistricts() {
        return districtRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<District> getDistrict(@PathVariable Long id) {
        Optional<District> district = districtRepository.findById(id);
        return district.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
