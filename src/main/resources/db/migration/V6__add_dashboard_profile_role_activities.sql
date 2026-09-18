-- Every role gets DASHBOARD (landing page) and PROFILE (self-service) - HOME is
-- retired in favour of DASHBOARD now that the app has a real dashboard.
-- HR_MANAGER additionally gets ROLE_LIST/ROLE_FORM for the new Role Management module.
UPDATE roles SET activities = JSON_ARRAY('SPLASH', 'LOGIN', 'DASHBOARD', 'PROFILE')
    WHERE role_name IN ('FACTORY_MANAGER', 'PRODUCTION_SUPERVISOR', 'IE_ANALYST', 'OPERATOR', 'QC_INSPECTOR', 'STORE_KEEPER');

UPDATE roles SET activities = JSON_ARRAY(
        'SPLASH', 'LOGIN', 'DASHBOARD', 'PROFILE',
        'EMPLOYEE_LIST', 'EMPLOYEE_DETAIL', 'EMPLOYEE_FORM',
        'ROLE_LIST', 'ROLE_FORM'
    )
    WHERE role_name = 'HR_MANAGER';
