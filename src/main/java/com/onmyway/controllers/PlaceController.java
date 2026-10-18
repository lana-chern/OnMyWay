package com.onmyway.controllers;

import com.onmyway.api.PlaceService;
import com.onmyway.auth.OnMyWayUserDetails;
import com.onmyway.api.dto.CreatePlaceRequest;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.UpdatePlaceRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {
    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) { this.placeService = placeService; }

    @GetMapping
    public List<PlaceResponse> findPublished(@RequestParam Long cityId,
                                               @RequestParam(required = false) String q) {
        return placeService.findPublished(cityId, q);
    }

    @GetMapping("/{id}")
    public PlaceResponse findById(@PathVariable Long id) { return placeService.findById(id); }

    @GetMapping("/{id}/manage")
    public PlaceResponse findForManagement(@PathVariable Long id,
                                           @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.findForManagement(id, principal.getId(), canManageAnyPlace(principal));
    }

    @PostMapping
    public ResponseEntity<PlaceResponse> create(@RequestBody CreatePlaceRequest request, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        PlaceResponse response = placeService.create(request, principal.getId());
        return ResponseEntity.created(URI.create("/api/places/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public PlaceResponse update(@PathVariable Long id, @RequestBody UpdatePlaceRequest request, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.update(id, request, principal.getId(), canManageAnyPlace(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        placeService.delete(id, principal.getId(), isAdmin(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit-for-moderation")
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
