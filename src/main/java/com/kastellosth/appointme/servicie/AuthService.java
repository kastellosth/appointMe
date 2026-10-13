package com.kastellosth.appointme.servicie;

import com.kastellosth.appointme.dto.LoginRequest;
import com.kastellosth.appointme.dto.RegisterRequest;
import com.kastellosth.appointme.dto.UserResponse;
import com.kastellosth.appointme.entity.User;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.UnauthorizedException;
import com.kastellosth.appointme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

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
    public UserResponse login(LoginRequest req) {
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
        return toResponse(user);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getCreatedAt());
    }
}