package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A tracked unit of WIP (PALMS §8), identified by a free-text reference rather than a
 * formal order/style/colour/size link - that master data doesn't exist yet. */
@Entity
@Table(name = "bundles")
public class Bundle {

    public enum Status { IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bundle_ref", length = 100, nullable = false, unique = true)
    private String bundleRef;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "current_station_id")
    private Station currentStation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "created_by_employee_id", nullable = false)
    private Long createdByEmployeeId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public String getBundleRef() { return bundleRef; }
    public void setBundleRef(String bundleRef) { this.bundleRef = bundleRef; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Station getCurrentStation() { return currentStation; }
    public void setCurrentStation(Station currentStation) { this.currentStation = currentStation; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Long getCreatedByEmployeeId() { return createdByEmployeeId; }
    public void setCreatedByEmployeeId(Long createdByEmployeeId) { this.createdByEmployeeId = createdByEmployeeId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
