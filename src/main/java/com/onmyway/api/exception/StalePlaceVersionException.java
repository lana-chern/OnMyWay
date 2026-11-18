package com.onmyway.api.exception;

public class StalePlaceVersionException extends RuntimeException {
    public StalePlaceVersionException(Long expectedVersion, Long actualVersion) {
        super("The place revision version is stale. Expected version %d but current version is %d. Reload the latest revision and try again."
                .formatted(expectedVersion, actualVersion));
    }
}
