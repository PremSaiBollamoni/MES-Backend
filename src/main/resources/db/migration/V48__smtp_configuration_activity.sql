-- SUPER_ADMIN bypasses activity checks at the API level, but the app's tab list is built
-- from the activity names on the role, so the Email settings tab needs the name listed.
UPDATE roles
SET activities = JSON_ARRAY_APPEND(activities, '$', 'SMTP_CONFIGURATION')
WHERE role_name = 'SUPER_ADMIN' AND NOT JSON_CONTAINS(activities, '"SMTP_CONFIGURATION"');
