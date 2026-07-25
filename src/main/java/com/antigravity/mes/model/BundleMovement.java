package com.antigravity.mes.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One IN or OUT scan event for a bundle at a station - the movement history behind
 * a bundle's current station and the WIP-per-station counts. */
@Entity
@Table(name = "bundle_movements")
public class BundleMovement {

    public enum ScanType { IN, OUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bundle_id", nullable = false)
    private Bundle bundle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private ScanType scanType;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt = LocalDateTime.now();

    @Column(name = "recorded_by_employee_id", nullable = false)
    private Long recordedByEmployeeId;

    public Long getId() { return id; }

    public Bundle getBundle() { return bundle; }
    public void setBundle(Bundle bundle) { this.bundle = bundle; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public ScanType getScanType() { return scanType; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }

    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }

    public Long getRecordedByEmployeeId() { return recordedByEmployeeId; }
    public void setRecordedByEmployeeId(Long recordedByEmployeeId) { this.recordedByEmployeeId = recordedByEmployeeId; }
}
