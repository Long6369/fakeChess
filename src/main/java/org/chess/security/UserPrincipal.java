package org.chess.security;

import io.quarkus.security.identity.SecurityIdentity;
import java.security.Principal;

public class UserPrincipal implements Principal {
    private final Long userId;
    private final String email;

    public UserPrincipal(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public static UserPrincipal from(SecurityIdentity identity) {
        Long userId = Long.parseLong(identity.getPrincipal().getName());
        String email = identity.getAttribute("email").toString();
        return new UserPrincipal(userId, email);
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}