package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.MembershipRole;

public record TenantTokenResponse(
        String accessToken, String tokenType, long expiresInSeconds,
        Long tenantId, MembershipRole role) {}