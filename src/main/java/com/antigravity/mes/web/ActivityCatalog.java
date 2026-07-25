package com.antigravity.mes.web;

import java.util.List;

/**
 * The full set of activity (screen) codes the app recognizes. Role Management uses
 * this to validate that a role's `activities` only ever contains real screens.
 * Keep in sync with frontend/lib/utils/activity_catalog.dart.
 */
public final class ActivityCatalog {

    public static final List<String> ALL = List.of(
            "SPLASH", "LOGIN", "DASHBOARD", "PROFILE",
            "EMPLOYEE_LIST", "EMPLOYEE_DETAIL", "EMPLOYEE_FORM",
            "ROLE_LIST", "ROLE_FORM",
            "ATTENDANCE",
            "STATION_LIST", "STATION_FORM",
            "OPERATIONS", "MY_PRODUCTION",
            "REPORTS"
    );

    private ActivityCatalog() {
    }
}
