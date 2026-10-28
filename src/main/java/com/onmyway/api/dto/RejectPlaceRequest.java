package com.onmyway.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reason and expected place version supplied by a moderator when rejecting a place submission.")
public record RejectPlaceRequest(
        @Schema(description = "Version of the place shown to the moderator.", example = "5")
        Long version,
        @Schema(description = "Explanation of why the submission was rejected.", example = "The address is incomplete.")
        String reason) {
}
