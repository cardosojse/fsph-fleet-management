ALTER TABLE role_permissions DROP CONSTRAINT chk_role_permissions_permission;
ALTER TABLE role_permissions ADD CONSTRAINT chk_role_permissions_permission CHECK (permission IN (
    'USERS_READ', 'USERS_WRITE', 'SECTORS_READ', 'SECTORS_WRITE',
    'PERMISSIONS_READ', 'PERMISSIONS_WRITE', 'VEHICLES_READ', 'VEHICLES_WRITE',
    'DRIVERS_READ', 'DRIVERS_WRITE', 'VEHICLES_ASSIGNED_READ', 'TRIPS_WRITE', 'TRIPS_READ'
));

-- Motoristas use only the assigned-vehicle route; gestores retain fleet-wide VEHICLES_READ.
DELETE FROM role_permissions WHERE role = 'MOTORISTA' AND permission = 'VEHICLES_READ';

INSERT INTO role_permissions (role, permission) VALUES
    ('MOTORISTA', 'VEHICLES_ASSIGNED_READ'),
    ('MOTORISTA', 'TRIPS_WRITE'),
    ('GESTOR_FROTA', 'TRIPS_READ'),
    ('ADMIN', 'VEHICLES_ASSIGNED_READ'),
    ('ADMIN', 'TRIPS_WRITE'),
    ('ADMIN', 'TRIPS_READ')
ON CONFLICT (role, permission) DO NOTHING;
