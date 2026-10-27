package com.onmyway.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Expected place version reviewed by a moderator before publishing.")
public record PublishPlaceRequest(
        @Schema(description = "Version of the place shown to the moderator.", example = "5")
        Long version) {
}
