package com.antigravity.mes.repository;

import com.antigravity.mes.model.EmployeeInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeInfoRepository extends JpaRepository<EmployeeInfo, Long> {
    Optional<EmployeeInfo> findByEmployeeId(Long employeeId);
    boolean existsByEmployeeCode(String employeeCode);
    Optional<EmployeeInfo> findByHrmsEmployeeCode(String hrmsEmployeeCode);
}
