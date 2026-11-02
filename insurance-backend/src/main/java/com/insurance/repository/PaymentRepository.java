package com.insurance.repository;
import com.insurance.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByPolId(Integer polId);
    @Query("SELECT pay FROM Payment pay WHERE pay.polId IN :policyIds")
    List<Payment> findByPolIdIn(@Param("policyIds") List<Integer> policyIds);
}
