package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.MembershipRole;

public record MembershipResponse(
        Long tenantId, String tenantName, String tenantSlug, MembershipRole role) {}