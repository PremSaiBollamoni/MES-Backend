package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_audit")
public class LoginAudit {

    public enum Result { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "username", length = 20, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private Result result;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt = LocalDateTime.now();

    public LoginAudit() {}

    public LoginAudit(Long employeeId, String username, Result result, String ipAddress) {
        this.employeeId = employeeId;
        this.username = username;
        this.result = result;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getUsername() {
        return username;
    }

    public Result getResult() {
        return result;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }
}
