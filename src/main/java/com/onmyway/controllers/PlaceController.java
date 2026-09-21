package com.onmyway.controllers;

import com.onmyway.api.PlaceService;
import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) { this.placeService = placeService; }

    @GetMapping
    public List<PlaceResponse> findPublished(@RequestParam Long cityId) { return placeService.findPublished(cityId); }

    @GetMapping("/{id}")
    public PlaceResponse findById(@PathVariable Long id) { return placeService.findById(id); }

    @PostMapping
    public ResponseEntity<PlaceResponse> create(@RequestBody CreatePlaceRequest request, Authentication authentication) {
        PlaceResponse response = placeService.create(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/places/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public PlaceResponse update(@PathVariable Long id, @RequestBody UpdatePlaceRequest request, Authentication authentication) {
        return placeService.update(id, request, authentication.getName());
    }
}
