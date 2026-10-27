package com.onmyway.auth;

public class TooManyRegistrationAttemptsException extends RuntimeException {
    public TooManyRegistrationAttemptsException() {
        super("Too many registration attempts. Please try again later.");
    }
}
