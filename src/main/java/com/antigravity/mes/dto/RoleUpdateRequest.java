package com.antigravity.mes.dto;

import java.util.List;

/** All fields optional - only non-null values are applied. */
public record RoleUpdateRequest(String roleName, List<String> activities, String status) {}
