package com.antigravity.mes.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

/**
 * A paid work station on the line - the Operation Bulletin / SAM sheet the IE / SAM
 * Analyst maintains. Every wage, efficiency, and bottleneck calculation reads from here.
 */
// Named sam_stations (not "stations") because a legacy, unused table from the
// original V1 schema draft already occupies that name.
@Entity
@Table(name = "sam_stations")
public class Station {

    public enum Zone { S1, S2 }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(name = "station_number", length = 20, nullable = false)
    private String stationNumber;

    @Column(name = "operation_name", length = 150, nullable = false)
    private String operationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false)
    private Zone zone;

    @Column(name = "sam", nullable = false)
    private double sam;

    @Column(name = "operator_count", nullable = false)
    private int operatorCount;

    @Column(name = "target_pieces_per_hour", nullable = false)
    private int targetPiecesPerHour;

    @Column(name = "target_pieces_per_day", nullable = false)
    private int targetPiecesPerDay;

    @Column(name = "rate_per_piece", nullable = false)
    private double ratePerPiece;

    /** Minimum input stock (pieces) this station needs on hand to be considered AVAILABLE
     * (PALMS §13) - Stock Availability compares the Supervisor's logged current quantity
     * against this instead of a manual AVAILABLE/NO_STOCK toggle. */
    @Column(name = "min_stock_threshold", nullable = false)
    private int minStockThreshold = 0;

    /** Ceiling above which stock is flagged HIGH_STOCK (overstock) - 0 means unset/no ceiling. */
    @Column(name = "max_stock_threshold", nullable = false)
    private int maxStockThreshold = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    public Station() {}

    public Long getId() { return id; }

    public Long getFactoryId() { return factoryId; }

    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }

    public String getStationNumber() { return stationNumber; }
    public void setStationNumber(String stationNumber) { this.stationNumber = stationNumber; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }

    public double getSam() { return sam; }
    public void setSam(double sam) { this.sam = sam; }

    public int getOperatorCount() { return operatorCount; }
    public void setOperatorCount(int operatorCount) { this.operatorCount = operatorCount; }

    public int getTargetPiecesPerHour() { return targetPiecesPerHour; }
    public void setTargetPiecesPerHour(int targetPiecesPerHour) { this.targetPiecesPerHour = targetPiecesPerHour; }

    public int getTargetPiecesPerDay() { return targetPiecesPerDay; }
    public void setTargetPiecesPerDay(int targetPiecesPerDay) { this.targetPiecesPerDay = targetPiecesPerDay; }

    public double getRatePerPiece() { return ratePerPiece; }
    public void setRatePerPiece(double ratePerPiece) { this.ratePerPiece = ratePerPiece; }

    public int getMinStockThreshold() { return minStockThreshold; }
    public void setMinStockThreshold(int minStockThreshold) { this.minStockThreshold = minStockThreshold; }

    public int getMaxStockThreshold() { return maxStockThreshold; }
    public void setMaxStockThreshold(int maxStockThreshold) { this.maxStockThreshold = maxStockThreshold; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
