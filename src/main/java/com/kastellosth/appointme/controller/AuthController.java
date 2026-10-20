package com.kastellosth.appointme.controller;

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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

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

    @PostMapping("/select-tenant/{tenantId}")
    public TenantTokenResponse selectTenant(@PathVariable Long tenantId) {
        return authService.selectTenant(currentUser.userId(), tenantId);
    }
}