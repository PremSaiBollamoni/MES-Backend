package com.antigravity.mes.repository;

import com.antigravity.mes.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
    Optional<Role> findByFactoryIdAndRoleName(Long factoryId, String roleName);

    List<Role> findByFactoryId(Long factoryId);
    List<Role> findByFactoryIdAndStatusEquals(Long factoryId, Role.Status status);
}
