package com.kastellosth.appointme.dto;

import com.kastellosth.appointme.entity.MembershipRole;

public record StaffResponse(
        Long membershipId, Long userId, String fullName, String email, MembershipRole role) {}