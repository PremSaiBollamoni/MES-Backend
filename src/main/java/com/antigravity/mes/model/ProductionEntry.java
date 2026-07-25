package com.antigravity.mes.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One operator's piece count at one station for one day, entered by the Production
 * Supervisor (operators have no app access to enter their own). SAM earned and
 * efficiency % are computed at write time from the station's SAM (PALMS §10). */
@Entity
@Table(name = "production_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "station_id", "shift_date"}))
public class ProductionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "pieces_produced", nullable = false)
    private int piecesProduced;

    @Column(name = "time_on_machine_minutes", nullable = false)
    private double timeOnMachineMinutes;

    @Column(name = "sam_earned", nullable = false)
    private double samEarned;

    @Column(name = "efficiency_pct", nullable = false)
    private double efficiencyPct;

    @Column(name = "entered_by_employee_id", nullable = false)
    private Long enteredByEmployeeId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public int getPiecesProduced() { return piecesProduced; }
    public void setPiecesProduced(int piecesProduced) { this.piecesProduced = piecesProduced; }

    public double getTimeOnMachineMinutes() { return timeOnMachineMinutes; }
    public void setTimeOnMachineMinutes(double timeOnMachineMinutes) { this.timeOnMachineMinutes = timeOnMachineMinutes; }

    public double getSamEarned() { return samEarned; }
    public void setSamEarned(double samEarned) { this.samEarned = samEarned; }

    public double getEfficiencyPct() { return efficiencyPct; }
    public void setEfficiencyPct(double efficiencyPct) { this.efficiencyPct = efficiencyPct; }

    public Long getEnteredByEmployeeId() { return enteredByEmployeeId; }
    public void setEnteredByEmployeeId(Long enteredByEmployeeId) { this.enteredByEmployeeId = enteredByEmployeeId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
