package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Pre-shift 4-tier stock check per station (PALMS §13). NO_STOCK (qty=0) is "starved";
 * LOW_STOCK is below minStockThreshold but not zero; HIGH_STOCK is above maxStockThreshold
 * (overstock); AVAILABLE is the normal band between the two. */
@Entity
@Table(name = "stock_availabilities", uniqueConstraints = @UniqueConstraint(columnNames = {"station_id", "shift_date"}))
public class StockAvailability {

    public enum Status { NO_STOCK, LOW_STOCK, AVAILABLE, HIGH_STOCK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /** What the Supervisor logged as on-hand today - status is derived from this vs. the
     * station's minStockThreshold, not chosen directly (PALMS §13 auto-availability). */
    @Column(name = "current_stock_qty", nullable = false)
    private int currentStockQty;

    @Column(name = "reason")
    private String reason;

    @Column(name = "logged_by_employee_id", nullable = false)
    private Long loggedByEmployeeId;

    @Column(name = "logged_at", nullable = false)
    private LocalDateTime loggedAt = LocalDateTime.now();

    public Long getId() { return id; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getCurrentStockQty() { return currentStockQty; }
    public void setCurrentStockQty(int currentStockQty) { this.currentStockQty = currentStockQty; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getLoggedByEmployeeId() { return loggedByEmployeeId; }
    public void setLoggedByEmployeeId(Long loggedByEmployeeId) { this.loggedByEmployeeId = loggedByEmployeeId; }

    public LocalDateTime getLoggedAt() { return loggedAt; }
    public void setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }
}
