package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.TenantRequest;
import com.kastellosth.appointme.dto.TenantResponse;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public TenantResponse create(TenantRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setSlug(request.slug());
        if (request.timezone() != null) {
            tenant.setTimezone(request.timezone());
        }

        Tenant saved = tenantRepository.save(tenant);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant " + id + " not found"));
        return toResponse(tenant);
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getTimezone(),
                tenant.getCreatedAt()
        );
    }
}