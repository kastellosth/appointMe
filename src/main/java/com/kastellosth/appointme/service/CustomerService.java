package com.kastellosth.appointme.service;

import com.kastellosth.appointme.dto.CustomerRequest;
import com.kastellosth.appointme.dto.CustomerResponse;
import com.kastellosth.appointme.dto.PageResponse;
import com.kastellosth.appointme.entity.Customer;
import com.kastellosth.appointme.entity.Tenant;
import com.kastellosth.appointme.exception.ConflictException;
import com.kastellosth.appointme.exception.NotFoundException;
import com.kastellosth.appointme.repository.CustomerRepository;
import com.kastellosth.appointme.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;
    private final TenantRepository tenantRepository;

    @Transactional
    public CustomerResponse create(Long tenantId, CustomerRequest req) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant " + tenantId + " not found"));
        Customer c = new Customer();
        c.setTenant(tenant);
        apply(c, req, tenantId);
        return toResponse(repository.save(c));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Long tenantId, String name, Pageable pageable) {
        Page<Customer> page = (name == null || name.isBlank())
                ? repository.findByTenantId(tenantId, pageable)
                : repository.findByTenantIdAndFullNameContainingIgnoreCase(
                tenantId, name.trim(), pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long tenantId, Long id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public CustomerResponse update(Long tenantId, Long id, CustomerRequest req) {
        Customer c = find(tenantId, id);
        apply(c, req, tenantId);
        return toResponse(c);
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        repository.delete(find(tenantId, id));
    }

    private Customer find(Long tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    private void apply(Customer c, CustomerRequest req, Long tenantId) {
        String email = (req.email() == null || req.email().isBlank())
                ? null : req.email().trim().toLowerCase();

        boolean emailChanged = email != null && !email.equals(c.getEmail());
        if (emailChanged && repository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new ConflictException("A customer with email '" + email + "' already exists");
        }

        c.setFullName(req.fullName().trim());
        c.setEmail(email);
        c.setPhone(req.phone());
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getFullName(), c.getEmail(),
                c.getPhone(), c.getCreatedAt());
    }
}
