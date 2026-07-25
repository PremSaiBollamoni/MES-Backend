package com.antigravity.mes.dto;

public record StationWipSummary(Long stationId, String stationNumber, String operationName, long bundleCount, boolean alertTriggered) {}
