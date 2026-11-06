package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.TenantBillingRequest;
import com.kastellosth.appointme.dto.TenantRequest;
import com.kastellosth.appointme.dto.TenantResponse;
import com.kastellosth.appointme.entity.Membership;
import com.kastellosth.appointme.entity.MembershipRole;
import com.kastellosth.appointme.entity.AuditAction;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.entity.User;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.exception.UnauthorizedException;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import com.kastellosth.appointme.repository.UserRepository;
import com.kastellosth.appointme.support.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final AuditService audit;

    @Transactional
    public TenantResponse create(Long ownerUserId, TenantRequest request) {
        User owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        if (tenantRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug '" + request.slug() + "' is already in use");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setSlug(request.slug());

        if (request.timezone() != null) {
            try {
                ZoneId.of(request.timezone());
            } catch (DateTimeException e) {
                throw new BadRequestException("Unknown time zone: " + request.timezone());
            }
            tenant.setTimezone(request.timezone());
        }

        Tenant saved = tenantRepository.save(tenant);

        Membership ownership = new Membership();
        ownership.setUser(owner);
        ownership.setTenant(saved);
        ownership.setRole(MembershipRole.OWNER);
        membershipRepository.save(ownership);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TenantResponse getById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tenant " + id + " not found"));
        return toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateBilling(Long tenantId, TenantBillingRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));
        String before = "tax " + tenant.getTaxRate() + " " + tenant.getCurrency();
        tenant.setTaxRate(Money.exactRate(request.taxRate()));
        tenant.setCurrency(request.currency());
        Tenant saved = tenantRepository.saveAndFlush(tenant);
        audit.record(tenantId, AuditAction.TENANT_BILLING_CHANGED, tenantId,
                before + " -> tax " + saved.getTaxRate() + " " + saved.getCurrency());
        return toResponse(saved);
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(),
                tenant.getTimezone(), tenant.getTaxRate(), tenant.getCurrency(), tenant.getCreatedAt());
    }
}
