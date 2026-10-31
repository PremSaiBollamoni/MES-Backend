package com.antigravity.mes.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** One operator's piece count at one station for one stretch of one day, entered by the
 * Production Supervisor (operators have no app access to enter their own). SAM earned and
 * efficiency % are computed at write time from the station's SAM (PALMS §10).
 *
 * No unique constraint on (employee, station, date): V22 removed it deliberately, since an
 * operator can legitimately work the same station several times in a day and each stretch
 * is its own entry rather than an overwrite of the last. */
@Entity
@Table(name = "production_entries")
public class ProductionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    /** Clock time this stretch of work started and finished. Nullable because entries
     * logged before hourly slots existed have no recorded time - the UI groups those
     * under the day rather than an hour. */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "pieces_produced", nullable = false)
    private int piecesProduced;

    /** Free-text colour of the fabric/batch being worked at this station for this entry. */
    @Column(name = "colour", length = 50)
    private String colour;

    @Column(name = "time_on_machine_minutes", nullable = false)
    private double timeOnMachineMinutes;

    @Column(name = "sam_earned", nullable = false)
    private double samEarned;

    @Column(name = "efficiency_pct", nullable = false)
    private double efficiencyPct;

    @Column(name = "entered_by_employee_id", nullable = false)
    private Long enteredByEmployeeId;

    /** How many times this entry has been corrected after its initial submission. Capped at
     * MAX_EDITS by the service layer - this column just has to persist the count. */
    @Column(name = "edit_count", nullable = false)
    private int editCount;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public Long getFactoryId() { return factoryId; }

    public void setFactoryId(Long factoryId) { this.factoryId = factoryId; }

    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public int getPiecesProduced() { return piecesProduced; }
    public void setPiecesProduced(int piecesProduced) { this.piecesProduced = piecesProduced; }

    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }

    public double getTimeOnMachineMinutes() { return timeOnMachineMinutes; }
    public void setTimeOnMachineMinutes(double timeOnMachineMinutes) { this.timeOnMachineMinutes = timeOnMachineMinutes; }

    public double getSamEarned() { return samEarned; }
    public void setSamEarned(double samEarned) { this.samEarned = samEarned; }

    public double getEfficiencyPct() { return efficiencyPct; }
    public void setEfficiencyPct(double efficiencyPct) { this.efficiencyPct = efficiencyPct; }

    public Long getEnteredByEmployeeId() { return enteredByEmployeeId; }
    public void setEnteredByEmployeeId(Long enteredByEmployeeId) { this.enteredByEmployeeId = enteredByEmployeeId; }

    public int getEditCount() { return editCount; }
    public void setEditCount(int editCount) { this.editCount = editCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
