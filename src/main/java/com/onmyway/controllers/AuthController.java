package com.onmyway.controllers;

import com.onmyway.auth.AuthService;
import com.onmyway.auth.ClientIpAddressResolver;
import com.onmyway.auth.InvalidAuthenticationException;
import com.onmyway.auth.LoginRateLimiter;
import com.onmyway.auth.TurnstileCaptchaVerifier;
import com.onmyway.auth.api.CurrentUserResponse;
import com.onmyway.auth.api.LoginRequest;
import com.onmyway.auth.api.LoginResponse;
import com.onmyway.auth.api.RefreshTokenRequest;
import com.onmyway.auth.api.RegisterRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientIpAddressResolver clientIpAddressResolver;
    private final TurnstileCaptchaVerifier captchaVerifier;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter,
                          ClientIpAddressResolver clientIpAddressResolver,
                          TurnstileCaptchaVerifier captchaVerifier) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.clientIpAddressResolver = clientIpAddressResolver;
        this.captchaVerifier = captchaVerifier;
    }

    @PostMapping("/register")
    public ResponseEntity<CurrentUserResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String email = request == null ? "" : request.email();
        String ipAddress = clientIpAddressResolver.resolve(servletRequest);
        LoginRateLimiter.Attempt attempt = loginRateLimiter.reserveAttempt(ipAddress, email);
        try {
            loginRateLimiter.applyDelay(attempt);
            if (attempt.requiresAdditionalVerification()
                    && (!captchaVerifier.isConfigured()
                    || !captchaVerifier.verify(request == null ? null : request.captchaToken(), ipAddress))) {
                throw new InvalidAuthenticationException("Invalid email or password");
            }
            LoginResponse response = authService.login(request);
            loginRateLimiter.recordSuccess(attempt);
            return ResponseEntity.ok(response);
        } catch (InvalidAuthenticationException exception) {
            loginRateLimiter.recordFailure(attempt);
            throw exception;
        } catch (RuntimeException exception) {
            // Validation and infrastructure errors must release the in-flight reservation,
            // but must not be counted as failed credentials.
            loginRateLimiter.recordSuccess(attempt);
            throw exception;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.currentUser(authentication.getName()));
    }
}
