package com.onmyway.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reason supplied by a moderator when rejecting a place submission.")
public record RejectPlaceRequest(
        @Schema(description = "Explanation of why the submission was rejected.", example = "The address is incomplete.")
        String reason) {
}
