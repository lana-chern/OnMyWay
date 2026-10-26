package com.onmyway.auth;

public class LoginAttemptInterruptedException extends RuntimeException {
    public LoginAttemptInterruptedException() {
        super("Login attempt was interrupted");
    }
}
