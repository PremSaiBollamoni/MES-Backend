package com.antigravity.mes.repository;

import com.antigravity.mes.model.Bundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BundleRepository extends JpaRepository<Bundle, Long> {
    List<Bundle> findByStatusOrderByCreatedAtDesc(Bundle.Status status);
    boolean existsByBundleRef(String bundleRef);
}
