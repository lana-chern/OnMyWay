package com.onmyway.auth;

/**
 * Signals that the supplied credentials or authentication token are not valid.
 */
public class InvalidAuthenticationException extends IllegalArgumentException {
    public InvalidAuthenticationException(String message) {
        super(message);
    }
}
