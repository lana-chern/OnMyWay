package com.onmyway.controllers;

import com.onmyway.api.PlaceService;
import com.onmyway.auth.OnMyWayUserDetails;
import com.onmyway.api.dto.PlaceResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderation/places")
public class ModerationController {
    private final PlaceService placeService;

    public ModerationController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/pending")
    public List<PlaceResponse> findPending(@AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.findPendingModeration(canModerate(principal));
    }

    @PostMapping("/{id}/publish")
    public PlaceResponse publish(@PathVariable Long id, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.publish(id, canModerate(principal));
    }

    @PostMapping("/{id}/reject")
    public PlaceResponse reject(@PathVariable Long id, @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.reject(id, canModerate(principal));
    }

    private boolean canModerate(OnMyWayUserDetails principal) {
        return principal.getAuthorities().stream().anyMatch(authority ->
                "ROLE_MODERATOR".equals(authority.getAuthority()) || "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
