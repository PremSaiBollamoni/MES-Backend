package com.antigravity.mes.repository;

import com.antigravity.mes.model.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
    boolean existsByWorkDate(LocalDate workDate);
    boolean existsByFactoryIdAndWorkDate(Long factoryId, LocalDate workDate);
    List<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(LocalDate from, LocalDate to);
    List<AttendanceRecord> findByEmployeeIdOrderByWorkDateDesc(Long employeeId);

    List<AttendanceRecord> findByFactoryIdAndWorkDate(Long factoryId, LocalDate workDate);
    List<AttendanceRecord> findByFactoryIdAndWorkDateBetweenOrderByWorkDateDescEmployeeNameAsc(Long factoryId, LocalDate from, LocalDate to);
}
