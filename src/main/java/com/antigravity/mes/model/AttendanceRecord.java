package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A day's attendance for one employee, pulled from HRMS and kept locally so history survives beyond whatever retention window the HRMS API itself exposes. */
@Entity
@Table(name = "attendance_records", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "work_date"}))
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 150, nullable = false)
    private String employeeName;

    @Column(name = "employee_code", length = 30, nullable = false)
    private String employeeCode;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "first_in", length = 20)
    private String firstIn;

    @Column(name = "last_out", length = 20)
    private String lastOut;

    @Column(name = "total_hours")
    private Double totalHours;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "is_late", nullable = false)
    private boolean late;

    @Column(name = "late_minutes")
    private Integer lateMinutes;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt = LocalDateTime.now();

    public Long getId() { return id; }

    public Long getFactoryId() { return factoryId; }

    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public String getFirstIn() { return firstIn; }
    public void setFirstIn(String firstIn) { this.firstIn = firstIn; }

    public String getLastOut() { return lastOut; }
    public void setLastOut(String lastOut) { this.lastOut = lastOut; }

    public Double getTotalHours() { return totalHours; }
    public void setTotalHours(Double totalHours) { this.totalHours = totalHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isLate() { return late; }
    public void setLate(boolean late) { this.late = late; }

    public Integer getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
}
