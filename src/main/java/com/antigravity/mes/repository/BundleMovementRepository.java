package com.antigravity.mes.repository;

import com.antigravity.mes.model.BundleMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BundleMovementRepository extends JpaRepository<BundleMovement, Long> {
    List<BundleMovement> findByBundleIdOrderByScannedAtAsc(Long bundleId);
}
