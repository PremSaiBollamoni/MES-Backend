package com.antigravity.mes.mail;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mail_recipients")
public class MailRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 150, nullable = false, unique = true)
    private String email;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "receives_payroll", nullable = false)
    private boolean receivesPayroll = true;

    @Column(name = "receives_attendance", nullable = false)
    private boolean receivesAttendance = true;

    @Column(name = "receives_production", nullable = false)
    private boolean receivesProduction = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isReceivesPayroll() { return receivesPayroll; }
    public void setReceivesPayroll(boolean receivesPayroll) { this.receivesPayroll = receivesPayroll; }

    public boolean isReceivesAttendance() { return receivesAttendance; }
    public void setReceivesAttendance(boolean receivesAttendance) { this.receivesAttendance = receivesAttendance; }

    public boolean isReceivesProduction() { return receivesProduction; }
    public void setReceivesProduction(boolean receivesProduction) { this.receivesProduction = receivesProduction; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
