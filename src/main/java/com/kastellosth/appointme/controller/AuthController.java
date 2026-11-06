package com.kastellosth.appointme.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.kastellosth.appointme.dto.LoginRequest;
import com.kastellosth.appointme.dto.LoginResponse;
import com.kastellosth.appointme.dto.MembershipResponse;
import com.kastellosth.appointme.dto.RegisterRequest;
import com.kastellosth.appointme.dto.TenantTokenResponse;
import com.kastellosth.appointme.dto.UserResponse;
import com.kastellosth.appointme.security.CurrentUser;
import com.kastellosth.appointme.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "1. Authentication", description = "Register, log in, then select a business to get a tenant token.")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @SecurityRequirements
    @Operation(summary = "Log in", description = "Returns a user token. Use it on POST /auth/select-tenant/{tenantId}.")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.currentUser(currentUser.userId());
    }

    @GetMapping("/memberships")
    public List<MembershipResponse> memberships() {
        return authService.myMemberships(currentUser.userId());
    }

    @Operation(summary = "Select a business", description = "Returns the tenant token that the rest of the API needs. Click Authorize again and paste it.")
    @PostMapping("/select-tenant/{tenantId}")
    public TenantTokenResponse selectTenant(@PathVariable Long tenantId) {
        return authService.selectTenant(currentUser.userId(), tenantId);
    }
}