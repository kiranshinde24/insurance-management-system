package com.insurance.repository;
import com.insurance.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {
    Optional<Customer> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByPan(String pan);
    List<Customer> findByAgentId(Integer agentId);
    @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.pan) LIKE LOWER(CONCAT('%',:q,'%')) OR c.phone LIKE CONCAT('%',:q,'%')")
    List<Customer> search(@Param("q") String q);
}
