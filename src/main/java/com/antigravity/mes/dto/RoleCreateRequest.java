package com.antigravity.mes.dto;

import java.util.List;

public record RoleCreateRequest(String roleName, List<String> activities) {}
