package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.*;
import com.kastellosth.appointme.entity.Membership;
import com.kastellosth.appointme.entity.User;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.exception.UnauthorizedException;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final MembershipRepository memberships;

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = normalize(req.email());

        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(req.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(req.password()));

        return toResponse(users.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        String email = normalize(req.email());

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, req.password()));
        } catch (AuthenticationException e) {
            // Same message for "unknown email" and "wrong password". Never reveal which.
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = users.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        return new LoginResponse(jwtService.issue(user), "Bearer",
                jwtService.ttlSeconds(), toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return users.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> myMemberships(Long userId) {
        return memberships.findByUserId(userId).stream()
                .map(m -> new MembershipResponse(
                        m.getTenant().getId(), m.getTenant().getName(),
                        m.getTenant().getSlug(), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantTokenResponse selectTenant(Long userId, Long tenantId) {
        Membership m = memberships.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));
        User user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        String token = jwtService.issueForTenant(user, tenantId, m.getId(), m.getRole());
        return new TenantTokenResponse(token, "Bearer", jwtService.ttlSeconds(), tenantId, m.getRole());
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getCreatedAt());
    }
}