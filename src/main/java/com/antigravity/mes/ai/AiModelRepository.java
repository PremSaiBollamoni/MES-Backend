package com.antigravity.mes.ai;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    boolean existsByProviderIdAndModelName(Long providerId, String modelName);
}
