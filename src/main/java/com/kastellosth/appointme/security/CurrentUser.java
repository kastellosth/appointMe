package com.kastellosth.appointme.security;

import com.kastellosth.appointme.entity.MembershipRole;
import com.kastellosth.appointme.exception.ForbiddenException;
import com.kastellosth.appointme.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Long userId() {
        return Long.valueOf(jwt().getSubject());
    }

    public Long tenantId() {
        Number tenantId = jwt().getClaim("tenant_id");
        if (tenantId == null) {
            throw new ForbiddenException(
                    "No business selected: call POST /auth/select-tenant/{tenantId} first");
        }
        return tenantId.longValue();
    }

    public MembershipRole role() {
        String role = jwt().getClaim("role");
        if (role == null) {
            throw new ForbiddenException("No business selected");
        }
        return MembershipRole.valueOf(role);
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new UnauthorizedException("Not authenticated");
    }
}