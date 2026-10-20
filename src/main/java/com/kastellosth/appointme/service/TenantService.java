package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.TenantRequest;
import com.kastellosth.appointme.dto.TenantResponse;
import com.kastellosth.appointme.entity.Membership;
import com.kastellosth.appointme.entity.MembershipRole;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.entity.User;
import com.kastellosth.appointme.exception.BadRequestException;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.exception.UnauthorizedException;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import com.kastellosth.appointme.repository.UserRepository;
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

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug(),
                tenant.getTimezone(), tenant.getCreatedAt());
    }
}