package com.onmyway.auth;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException() {
        super("Too many failed login attempts. Try again later.");
    }
}
