package com.kastellosth.appointme.servicie;

import com.kastellosth.appointme.dto.ServiceOfferingRequest;
import com.kastellosth.appointme.dto.ServiceOfferingResponse;
import com.kastellosth.appointme.entity.ServiceOffering;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.repository.ServiceOfferingRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceOfferingService {

    private final ServiceOfferingRepository repository;
    private final TenantRepository tenantRepository;

    @Transactional
    public ServiceOfferingResponse create(Long tenantId, ServiceOfferingRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));
        ServiceOffering s = new ServiceOffering();
        s.setTenant(tenant);
        apply(s, req);
        return toResponse(repository.save(s));
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> list(Long tenantId) {
        return repository.findAllByTenantId(tenantId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse get(Long tenantId, Long id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public ServiceOfferingResponse update(Long tenantId, Long id, ServiceOfferingRequest req) {
        ServiceOffering s = find(tenantId, id);
        apply(s, req);
        return toResponse(s);   // changes are saved automatically at commit
    }

    @Transactional
    public void deactivate(Long tenantId, Long id) {
        find(tenantId, id).setActive(false);   // soft delete
    }

    private ServiceOffering find(Long tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RuntimeException("Service not found"));
    }

    private void apply(ServiceOffering s, ServiceOfferingRequest req) {
        s.setName(req.name());
        s.setDurationMinutes(req.durationMinutes());
        s.setPrice(req.price());
        s.setActive(req.active() == null || req.active());
    }

    private ServiceOfferingResponse toResponse(ServiceOffering s) {
        return new ServiceOfferingResponse(s.getId(), s.getName(),
                s.getDurationMinutes(), s.getPrice(), s.isActive(), s.getCreatedAt());
    }
}
