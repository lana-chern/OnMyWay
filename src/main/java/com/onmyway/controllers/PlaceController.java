package com.onmyway.controllers;

import com.onmyway.api.PlaceService;
import com.onmyway.auth.OnMyWayUserDetails;
import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.PublishedPlacesPageResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/places")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) { this.placeService = placeService; }

    @GetMapping
    public PublishedPlacesPageResponse findPublished(@RequestParam Long cityId,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PublishedPlacesPageResponse.from(placeService.findPublished(cityId, q, page, size));
    }

    @GetMapping("/{id}")
    public PlaceResponse findById(@PathVariable Long id) { return placeService.findById(id); }

    @GetMapping("/{id}/manage")
    @SecurityRequirement(name = "bearerAuth")
    public PlaceResponse findForManagement(@PathVariable Long id,
                                           @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.findForManagement(id, principal.getId(), canManageAnyPlace(principal));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PlaceResponse> create(@RequestBody CreatePlaceRequest request, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        PlaceResponse response = placeService.create(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/places/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public PlaceResponse update(@PathVariable Long id, @RequestBody UpdatePlaceRequest request, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.update(id, request, principal.getId(), canManageAnyPlace(principal));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        placeService.delete(id, principal.getId(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit-for-moderation")
    @SecurityRequirement(name = "bearerAuth")
    public PlaceResponse submitForModeration(@PathVariable Long id, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.submitForModeration(id, principal.getId());
    }

    private boolean canManageAnyPlace(OnMyWayUserDetails principal) {
        return hasRole(principal, "ROLE_MODERATOR") || isAdmin(principal);
    }

    private boolean isAdmin(OnMyWayUserDetails principal) {
        return hasRole(principal, "ROLE_ADMIN");
    }

    private boolean hasRole(OnMyWayUserDetails principal, String role) {
        return principal.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
