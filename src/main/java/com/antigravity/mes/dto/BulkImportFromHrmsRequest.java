package com.antigravity.mes.dto;

/**
 * Role applied to every employee pulled in by the one-click "sync all" action.
 * defaultDepartment is optional - when blank, each employee keeps whatever department
 * HRMS already has on file for them.
 */
public record BulkImportFromHrmsRequest(String defaultRoleCode, String defaultDepartment) {}
