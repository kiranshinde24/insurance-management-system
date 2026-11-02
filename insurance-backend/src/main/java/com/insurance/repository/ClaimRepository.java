package com.insurance.repository;
import com.insurance.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Integer> {
    List<Claim> findByPolId(Integer polId);
    List<Claim> findByStatus(String status);
    @Query("SELECT c FROM Claim c WHERE c.polId IN :policyIds")
    List<Claim> findByPolIdIn(@Param("policyIds") List<Integer> policyIds);
}
