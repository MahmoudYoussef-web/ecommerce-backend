package com.mahmoud.ecommerce_backend.security.user;

import com.mahmoud.ecommerce_backend.entity.User;
import lombok.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@AllArgsConstructor
public class CustomUserPrincipal implements UserDetails {

    private Long userId;
    private String email;
    private String password;
    private boolean accountNonLocked;
    private boolean enabled;
    private Integer tokenVersion;
    private Long tenantId;
    private Collection<? extends GrantedAuthority> authorities;

    public static CustomUserPrincipal from(User user, List<String> roles) {
        return new CustomUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isAccountNonLocked(),
                user.isEnabled(),
                user.getTokenVersion(),
                user.getTenantId(),
                roles.stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    public boolean hasRole(String role) {
        return authorities.stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}