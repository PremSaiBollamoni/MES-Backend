package com.antigravity.mes.repository;

import com.antigravity.mes.model.TrackingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackingSessionRepository extends JpaRepository<TrackingSession, Long> {

    @Query("select ts from TrackingSession ts join ts.employees e where e.id = :employeeId and ts.status = :status")
    Optional<TrackingSession> findActiveForEmployee(@Param("employeeId") Long employeeId, @Param("status") TrackingSession.Status status);

    List<TrackingSession> findByShiftDateOrderByStartedAtDesc(LocalDate shiftDate);
}
