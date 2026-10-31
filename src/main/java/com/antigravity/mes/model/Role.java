package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "roles")
public class Role {

    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_name", length = 50, nullable = false)
    private String roleName;

    @Column(name = "factory_id")
    private Long factoryId;

    /** Activity (screen) codes this role may access - see frontend/lib/activities/. */
    @Convert(converter = ActivityListConverter.class)
    @Column(name = "activities", columnDefinition = "JSON", nullable = false)
    private List<String> activities;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    public Role() {}

    public Long getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public List<String> getActivities() {
        return activities;
    }

    public void setActivities(List<String> activities) {
        this.activities = activities;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean canAccess(String activityCode) {
        return activities != null && activities.contains(activityCode);
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    /**
     * True only for the one system-wide SUPER_ADMIN role. Roles are otherwise global
     * (shared by every factory, not duplicated per factory) - factory_id on a normal
     * role is legacy/unused, kept only because SUPER_ADMIN also has a NULL factory_id
     * and the column remains for that one row.
     */
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(roleName);
    }
}

