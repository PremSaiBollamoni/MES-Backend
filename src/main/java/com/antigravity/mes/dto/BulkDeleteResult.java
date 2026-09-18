package com.antigravity.mes.dto;

import java.util.List;

public record BulkDeleteResult(int deletedCount, List<BulkDeleteFailure> failed) {

    public record BulkDeleteFailure(Long id, String reason) {}
}
