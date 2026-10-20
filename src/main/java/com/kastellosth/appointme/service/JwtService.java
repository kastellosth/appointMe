package com.kastellosth.appointme.service;

import com.kastellosth.appointme.entity.MembershipRole;
import com.kastellosth.appointme.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** Token for a logged-in user who has not chosen a business yet. */
    public String issue(User user) {
        return encode(baseClaims(user).build());
    }

    /** Token scoped to one business: carries tenant, membership and role. */
    public String issueForTenant(User user, Long tenantId, Long membershipId, MembershipRole role) {
        return encode(baseClaims(user)
                .claim("tenant_id", tenantId)
                .claim("membership_id", membershipId)
                .claim("role", role.name())
                .build());
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    private JwtClaimsSet.Builder baseClaims(User user) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer("appointme")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail());
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}