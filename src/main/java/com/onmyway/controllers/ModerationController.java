package com.onmyway.controllers;

import com.onmyway.api.PlaceService;
import com.onmyway.auth.OnMyWayUserDetails;
import com.onmyway.api.dto.PlaceResponse;
import com.onmyway.api.dto.RejectPlaceRequest;
import com.onmyway.api.dto.PublishPlaceRequest;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moderation/places")
@SecurityRequirement(name = "bearerAuth")
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
    public PlaceResponse publish(
            @Parameter(description = "Unique ID of the pending place submission to publish.", example = "42")
            @PathVariable Long id,
            @RequestBody(description = "Expected version of the place reviewed by the moderator.",
                    required = true, content = @Content(schema = @Schema(implementation = PublishPlaceRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody PublishPlaceRequest request,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.publish(id, canModerate(principal), request.version());
    }

    @PostMapping("/{id}/reject")
    public PlaceResponse reject(
            @Parameter(description = "Unique ID of the pending place submission to reject.", example = "42")
            @PathVariable Long id,
            @RequestBody(description = "Expected version and rejection reason for the reviewed submission.",
                    required = true, content = @Content(schema = @Schema(implementation = RejectPlaceRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody RejectPlaceRequest request,
            @AuthenticationPrincipal OnMyWayUserDetails principal) {
        return placeService.reject(id, canModerate(principal), request.version(), request.reason());
    }

    private boolean canModerate(OnMyWayUserDetails principal) {
        return principal.getAuthorities().stream().anyMatch(authority ->
                "ROLE_MODERATOR".equals(authority.getAuthority()) || "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
