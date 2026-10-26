package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.StaffResponse;
import com.kastellosth.appointme.entity.Membership;
import com.kastellosth.appointme.entity.MembershipRole;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.entity.User;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.MembershipRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import com.kastellosth.appointme.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final MembershipRepository memberships;
    private final UserRepository users;
    private final TenantRepository tenants;

    @Transactional
    public StaffResponse add(Long tenantId, String email) {
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException("No registered user with that email"));

        if (memberships.existsByUserIdAndTenantId(user.getId(), tenantId)) {
            throw new ConflictException("That user is already a member of this business");
        }

        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));

        Membership m = new Membership();
        m.setUser(user);
        m.setTenant(tenant);
        m.setRole(MembershipRole.STAFF);
        return toResponse(memberships.save(m));
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list(Long tenantId) {
        return memberships.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(this::toResponse).toList();
    }

    private StaffResponse toResponse(Membership m) {
        return new StaffResponse(m.getId(), m.getUser().getId(), m.getUser().getFullName(),
                m.getUser().getEmail(), m.getRole());
    }
}