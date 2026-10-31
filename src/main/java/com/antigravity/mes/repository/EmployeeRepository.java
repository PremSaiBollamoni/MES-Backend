package com.antigravity.mes.repository;

import com.antigravity.mes.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUsername(String username);
    Optional<Employee> findByFactoryIdAndUsername(Long factoryId, String username);

    boolean existsByUsername(String username);

    List<Employee> findByFactoryId(Long factoryId);
    List<Employee> findByFactoryIdAndActiveTrue(Long factoryId);
}
