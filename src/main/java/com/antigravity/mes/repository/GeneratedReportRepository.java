package com.antigravity.mes.repository;

import com.antigravity.mes.model.GeneratedReport;
import com.antigravity.mes.report.PeriodType;
import com.antigravity.mes.report.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, Long> {
    Optional<GeneratedReport> findByFactoryIdAndReportDateAndReportTypeAndPeriodType(Long factoryId, LocalDate reportDate, ReportType reportType, PeriodType periodType);
    List<GeneratedReport> findAllByFactoryIdAndReportTypeOrderByReportDateDesc(Long factoryId, ReportType reportType);
}
