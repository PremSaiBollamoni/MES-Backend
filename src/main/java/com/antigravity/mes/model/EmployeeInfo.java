package com.antigravity.mes.model;

import com.antigravity.mes.security.EncryptedStringConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_info")
public class EmployeeInfo {

    public enum Gender { MALE, FEMALE, OTHER }

    public enum HrmsSyncStatus { NOT_SYNCED, SYNCED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Column(name = "employee_code", length = 30, nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "hrms_employee_id")
    private Integer hrmsEmployeeId;

    @Column(name = "hrms_employee_code", length = 30)
    private String hrmsEmployeeCode;

    @Column(name = "hrms_device_user_id", length = 20)
    private String hrmsDeviceUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hrms_sync_status", nullable = false)
    private HrmsSyncStatus hrmsSyncStatus = HrmsSyncStatus.NOT_SYNCED;

    @Column(name = "hrms_synced_at")
    private LocalDateTime hrmsSyncedAt;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    @Column(name = "shift", length = 20, nullable = false)
    private String shift = "GENERAL";

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_encrypted", length = 1000)
    private String address;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "emergency_contact_number_encrypted", length = 255)
    private String emergencyContactNumber;

    @Column(name = "emergency_contact_relationship", length = 50)
    private String emergencyContactRelationship;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    public EmployeeInfo() {}

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public Integer getHrmsEmployeeId() {
        return hrmsEmployeeId;
    }

    public void setHrmsEmployeeId(Integer hrmsEmployeeId) {
        this.hrmsEmployeeId = hrmsEmployeeId;
    }

    public String getHrmsEmployeeCode() {
        return hrmsEmployeeCode;
    }

    public void setHrmsEmployeeCode(String hrmsEmployeeCode) {
        this.hrmsEmployeeCode = hrmsEmployeeCode;
    }

    public String getHrmsDeviceUserId() {
        return hrmsDeviceUserId;
    }

    public void setHrmsDeviceUserId(String hrmsDeviceUserId) {
        this.hrmsDeviceUserId = hrmsDeviceUserId;
    }

    public HrmsSyncStatus getHrmsSyncStatus() {
        return hrmsSyncStatus;
    }

    public void setHrmsSyncStatus(HrmsSyncStatus hrmsSyncStatus) {
        this.hrmsSyncStatus = hrmsSyncStatus;
    }

    public LocalDateTime getHrmsSyncedAt() {
        return hrmsSyncedAt;
    }

    public void setHrmsSyncedAt(LocalDateTime hrmsSyncedAt) {
        this.hrmsSyncedAt = hrmsSyncedAt;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactNumber() {
        return emergencyContactNumber;
    }

    public void setEmergencyContactNumber(String emergencyContactNumber) {
        this.emergencyContactNumber = emergencyContactNumber;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }

    public void setEmergencyContactRelationship(String emergencyContactRelationship) {
        this.emergencyContactRelationship = emergencyContactRelationship;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
