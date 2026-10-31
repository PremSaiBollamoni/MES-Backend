package com.antigravity.mes.repository;

import com.antigravity.mes.model.Factory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FactoryRepository extends JpaRepository<Factory, Long> {
    Optional<Factory> findByFactoryCode(String factoryCode);
    List<Factory> findByActiveTrue();
}
