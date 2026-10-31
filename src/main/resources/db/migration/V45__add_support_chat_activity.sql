-- Every role gets SUPPORT_CHAT (chat with the AI assistant + raise a ticket) - it's a
-- self-help feature for app issues, not tied to any particular job function.
UPDATE roles
SET activities = JSON_ARRAY_APPEND(activities, '$', 'SUPPORT_CHAT')
WHERE NOT JSON_CONTAINS(activities, '"SUPPORT_CHAT"');

-- SUPER_ADMIN also gets SUPPORT_TICKETS_ADMIN (view/resolve every factory's tickets).
-- SUPER_ADMIN already bypasses activity checks entirely (see RoleAuthorizationInterceptor),
-- so this is just for consistency/documentation - no other role is given this activity,
-- which is what actually keeps the ticket inbox SUPER_ADMIN-only.
UPDATE roles
SET activities = JSON_ARRAY_APPEND(activities, '$', 'SUPPORT_TICKETS_ADMIN')
WHERE role_name = 'SUPER_ADMIN' AND NOT JSON_CONTAINS(activities, '"SUPPORT_TICKETS_ADMIN"');
