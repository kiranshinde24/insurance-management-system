package com.insurance.repository;
import com.insurance.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, Integer> {
    List<Policy> findByCustId(Integer custId);
    List<Policy> findByAgentId(Integer agentId);
    List<Policy> findByTypeId(Integer typeId);
    boolean existsByPolno(String polno);
    @Query("SELECT MAX(p.polno) FROM Policy p WHERE p.polno LIKE 'POL-%'")
    String findMaxPolno();
}
