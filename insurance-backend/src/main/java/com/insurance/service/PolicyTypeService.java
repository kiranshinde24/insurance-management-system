package com.insurance.service;

import com.insurance.exception.BadRequestException;
import com.insurance.exception.ConflictException;
import com.insurance.exception.ResourceNotFoundException;
import com.insurance.model.PolicyType;
import com.insurance.repository.PolicyTypeRepository;
import com.insurance.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
public class PolicyTypeService {

    private final PolicyTypeRepository repo;
    private final PolicyRepository policyRepo;

    public PolicyTypeService(PolicyTypeRepository repo, PolicyRepository policyRepo) {
        this.repo = repo;
        this.policyRepo = policyRepo;
    }

    public List<PolicyType> getAll() {
        return repo.findAll();
    }

    public PolicyType getById(Integer id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy type not found"));
    }

    @Transactional
    public PolicyType create(PolicyType type) {
        if (type.getName() == null || type.getName().isBlank())
            throw new BadRequestException("Type name is required");
        PolicyType saved = repo.save(type);
        log.info("Policy type created: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public PolicyType update(Integer id, PolicyType type) {
        PolicyType existing = getById(id);
        existing.setName(type.getName());
        existing.setDesc(type.getDesc());
        if (type.getBaseRate() != null) existing.setBaseRate(type.getBaseRate());
        if (type.getRiskFactor() != null) existing.setRiskFactor(type.getRiskFactor());
        PolicyType saved = repo.save(existing);
        log.info("Policy type updated: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Integer id) {
        // check if any policy uses this type
        if (!policyRepo.findByTypeId(id).isEmpty())
            throw new ConflictException("Cannot delete: policies exist for this type");
        repo.deleteById(id);
        log.info("Policy type deleted: id={}", id);
    }

    public long countByTypeId(Integer typeId) {
        return policyRepo.findByTypeId(typeId).size();
    }
}
