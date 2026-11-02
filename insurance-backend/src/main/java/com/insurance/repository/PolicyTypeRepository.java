package com.insurance.repository;
import com.insurance.model.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PolicyTypeRepository extends JpaRepository<PolicyType, Integer> {}
