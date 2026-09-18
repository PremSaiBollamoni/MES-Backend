-- Adds the ATTENDANCE activity (Live/Daily attendance from the HRMS biometric
-- portal integration) to HR and Production Supervisor, matching PALMS.md §5's
-- ownership of attendance ("Production Supervisor: Attendance").
UPDATE roles SET activities = JSON_ARRAY_APPEND(activities, '$', 'ATTENDANCE')
    WHERE role_name IN ('HR_MANAGER', 'PRODUCTION_SUPERVISOR');
