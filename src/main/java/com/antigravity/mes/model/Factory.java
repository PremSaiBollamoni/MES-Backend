package com.antigravity.mes.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "factories")
public class Factory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "factory_id")
    private Long factoryId;

    @Column(name = "factory_name", length = 150, nullable = false, unique = true)
    private String factoryName;

    @Column(name = "factory_code", length = 30, nullable = false, unique = true)
    private String factoryCode;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "hrms_enabled", nullable = false)
    private boolean hrmsEnabled = true;

    @Column(name = "hrms_config_json", columnDefinition = "JSON")
    private String hrmsConfigJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Factory() {}

    public Factory(String factoryName, String factoryCode, String location) {
        this.factoryName = factoryName;
        this.factoryCode = factoryCode;
        this.location = location;
        this.hrmsEnabled = true;
        this.active = true;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public void setFactoryId(Long factoryId) {
        this.factoryId = factoryId;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
    }

    public String getFactoryCode() {
        return factoryCode;
    }

    public void setFactoryCode(String factoryCode) {
        this.factoryCode = factoryCode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isHrmsEnabled() {
        return hrmsEnabled;
    }

    public void setHrmsEnabled(boolean hrmsEnabled) {
        this.hrmsEnabled = hrmsEnabled;
    }

    public String getHrmsConfigJson() {
        return hrmsConfigJson;
    }

    public void setHrmsConfigJson(String hrmsConfigJson) {
        this.hrmsConfigJson = hrmsConfigJson;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
