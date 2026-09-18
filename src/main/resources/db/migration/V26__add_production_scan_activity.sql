-- Scanning production books writes to the production log, so it is gated by its own activity
-- rather than riding on OPERATIONS: importing a fortnight of handwritten history is a different
-- privilege from logging one entry, and it should be grantable and revocable on its own.
--
-- Granted to the roles that already own production data entry. Everyone else is unaffected.

UPDATE roles
SET activities = JSON_ARRAY_APPEND(activities, '$', 'PRODUCTION_SCAN')
WHERE role_name IN ('FACTORY_MANAGER', 'PRODUCTION_SUPERVISOR')
  AND NOT JSON_CONTAINS(activities, '"PRODUCTION_SCAN"');
