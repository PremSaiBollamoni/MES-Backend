package com.antigravity.mes.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** A live "team is working right now" session (PALMS §10 real-time tracking). For a
 * multi-operator station (e.g. Fusing, 2 operators) the whole team shares ONE session, one
 * shared piece count, and one shared timer - wages are split per {@code station.operatorCount}
 * at read time, not stored per-employee. Started when the Production Supervisor issues pieces
 * to the allocated team, stopped once they finish - at which point it feeds one
 * {@link ProductionEntry} per team member with the pieces actually completed and the real
 * elapsed time on machine. */
@Entity
@Table(name = "tracking_sessions")
public class TrackingSession {

    public enum Status { ACTIVE, STOPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "tracking_session_employees",
            joinColumns = @JoinColumn(name = "tracking_session_id"),
            inverseJoinColumns = @JoinColumn(name = "employee_id"))
    private Set<Employee> employees = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "pieces_issued", nullable = false)
    private int piecesIssued;

    /** The WIP bundle auto-created for this session's issued pieces (PALMS §8/§10 linkage) -
     * auto-completed when the session stops. Nullable only for rows predating this link. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bundle_id")
    private Bundle bundle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "started_by_employee_id", nullable = false)
    private Long startedByEmployeeId;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Column(name = "pieces_completed")
    private Integer piecesCompleted;

    @Column(name = "stopped_by_employee_id")
    private Long stoppedByEmployeeId;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public Set<Employee> getEmployees() { return employees; }
    public void setEmployees(Set<Employee> employees) { this.employees = employees; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public int getPiecesIssued() { return piecesIssued; }
    public void setPiecesIssued(int piecesIssued) { this.piecesIssued = piecesIssued; }

    public Bundle getBundle() { return bundle; }
    public void setBundle(Bundle bundle) { this.bundle = bundle; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public Long getStartedByEmployeeId() { return startedByEmployeeId; }
    public void setStartedByEmployeeId(Long startedByEmployeeId) { this.startedByEmployeeId = startedByEmployeeId; }

    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(LocalDateTime stoppedAt) { this.stoppedAt = stoppedAt; }

    public Integer getPiecesCompleted() { return piecesCompleted; }
    public void setPiecesCompleted(Integer piecesCompleted) { this.piecesCompleted = piecesCompleted; }

    public Long getStoppedByEmployeeId() { return stoppedByEmployeeId; }
    public void setStoppedByEmployeeId(Long stoppedByEmployeeId) { this.stoppedByEmployeeId = stoppedByEmployeeId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
