package com.insurance.repository;
import com.insurance.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Integer> {
    Optional<Agent> findByUsername(String username);
    boolean existsByUsername(String username);
}
