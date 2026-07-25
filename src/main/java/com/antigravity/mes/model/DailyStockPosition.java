package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One zone's stock position for one day: Opening + Additions - Dispatched = Closing (PALMS §6). */
@Entity
@Table(name = "daily_stock_positions", uniqueConstraints = @UniqueConstraint(columnNames = {"zone_code", "position_date"}))
public class DailyStockPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_code", nullable = false)
    private StockZone zone;

    @Column(name = "position_date", nullable = false)
    private LocalDate positionDate;

    @Column(name = "opening_balance", nullable = false)
    private int openingBalance;

    @Column(name = "additions", nullable = false)
    private int additions;

    @Column(name = "dispatched", nullable = false)
    private int dispatched;

    @Column(name = "entered_by_employee_id", nullable = false)
    private Long enteredByEmployeeId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public int getClosingBalance() {
        return openingBalance + additions - dispatched;
    }

    public Long getId() { return id; }

    public StockZone getZone() { return zone; }
    public void setZone(StockZone zone) { this.zone = zone; }

    public LocalDate getPositionDate() { return positionDate; }
    public void setPositionDate(LocalDate positionDate) { this.positionDate = positionDate; }

    public int getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(int openingBalance) { this.openingBalance = openingBalance; }

    public int getAdditions() { return additions; }
    public void setAdditions(int additions) { this.additions = additions; }

    public int getDispatched() { return dispatched; }
    public void setDispatched(int dispatched) { this.dispatched = dispatched; }

    public Long getEnteredByEmployeeId() { return enteredByEmployeeId; }
    public void setEnteredByEmployeeId(Long enteredByEmployeeId) { this.enteredByEmployeeId = enteredByEmployeeId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
