package com.kastellosth.appointme.dto;

public record TenantRequest(
        String name,
        String slug,
        String timezone
) {
}