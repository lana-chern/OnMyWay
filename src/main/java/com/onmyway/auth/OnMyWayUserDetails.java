package com.onmyway.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public final class OnMyWayUserDetails implements UserDetails {
    private final Long id;
    private final String email;
    private final String password;
    private final List<GrantedAuthority> authorities;
    private final boolean enabled;
    private final long tokenVersion;

    public OnMyWayUserDetails(
            Long id,
            String email,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean enabled,
            long tokenVersion
    ) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
        this.tokenVersion = tokenVersion;
    }

    public Long getId() {
        return id;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
