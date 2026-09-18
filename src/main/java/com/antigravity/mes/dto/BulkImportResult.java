package com.antigravity.mes.dto;

import java.util.List;

public record BulkImportResult(List<EmployeeDetailResponse> imported, List<BulkImportFailure> failed) {

    public record BulkImportFailure(String hrmsEmployeeCode, String name, String reason) {}
}
