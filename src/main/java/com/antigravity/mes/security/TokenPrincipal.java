package com.antigravity.mes.security;

/** The identity carried by a validated session token. */
public record TokenPrincipal(Long employeeId, String role) {}
