package com.antigravity.mes.dto;

import java.util.List;

public record RoleDetailResponse(
        Long roleId,
        String roleName,
        List<String> activities,
        String status
) {}
