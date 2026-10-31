-- SUPER_ADMIN bypasses activity checks at the API level; the activity name lets the app show the AI settings tab.
UPDATE roles
SET activities = JSON_ARRAY_APPEND(activities, '$', 'AI_CONFIGURATION')
WHERE role_name = 'SUPER_ADMIN' AND NOT JSON_CONTAINS(activities, '"AI_CONFIGURATION"');
