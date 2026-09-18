-- Adds the PAYROLL activity (Salary Payroll Sheet Generation, HR module) to HR Manager,
-- the role that already owns wage-adjacent employee data.
UPDATE roles SET activities = JSON_ARRAY_APPEND(activities, '$', 'PAYROLL')
    WHERE role_name = 'HR_MANAGER'
      AND NOT JSON_CONTAINS(activities, '"PAYROLL"');
