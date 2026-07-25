package com.antigravity.mes.dto;

import java.util.List;

public record BulkDeleteRequest(List<Long> ids) {}
