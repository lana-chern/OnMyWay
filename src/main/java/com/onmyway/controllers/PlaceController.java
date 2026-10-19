package com.onmyway.controllers;

import com.onmyway.api.PlaceService;
import com.onmyway.auth.OnMyWayUserDetails;
import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.PublishedPlacesPageResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
    public PublishedPlacesPageResponse findPublished(
            @Parameter(description = "ID of the city whose published places should be returned.", example = "1")
            @RequestParam Long cityId,
            @Parameter(description = "Optional case-insensitive search text matched against place names.", example = "museum")
            @RequestParam(required = false) String q,
            @Parameter(description = "Zero-based page index. Defaults to 0.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of places per page (1–100). Defaults to 20.", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return PublishedPlacesPageResponse.from(placeService.findPublished(cityId, q, page, size));
    }

    @GetMapping("/{id}")
    public PlaceResponse findById(
            @Parameter(description = "Unique ID of the place.", example = "42")
            @PathVariable Long id) {
        return placeService.findById(id);
    }

    @GetMapping("/{id}/manage")
    @SecurityRequirement(name = "bearerAuth")
    public PlaceResponse findForManagement(
            @Parameter(description = "Unique ID of the place to manage.", example = "42")
            @PathVariable Long id,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.findForManagement(id, principal.getId(), canManageAnyPlace(principal));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PlaceResponse> create(
            @RequestBody(description = "Place details, including optional photos, contacts and opening hours.",
                    required = true, content = @Content(schema = @Schema(implementation = CreatePlaceRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody CreatePlaceRequest request,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
        PlaceResponse response = placeService.create(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/places/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public PlaceResponse update(
            @Parameter(description = "Unique ID of the place to update.", example = "42")
            @PathVariable Long id,
            @RequestBody(description = "Fields to update. The version must match the current place version.",
                    required = true, content = @Content(schema = @Schema(implementation = UpdatePlaceRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody UpdatePlaceRequest request,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.update(id, request, principal.getId(), canManageAnyPlace(principal));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Unique ID of the place to archive/delete according to the caller's permissions.", example = "42")
            @PathVariable Long id,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
        placeService.delete(id, principal.getId(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit-for-moderation")
    @SecurityRequirement(name = "bearerAuth")
    public PlaceResponse submitForModeration(
            @Parameter(description = "Unique ID of the place to submit for moderation.", example = "42")
            @PathVariable Long id,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
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
