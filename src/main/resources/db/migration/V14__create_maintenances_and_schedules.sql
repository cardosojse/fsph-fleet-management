ALTER TABLE vehicles
    ADD COLUMN in_maintenance BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_vehicles_availability
    ON vehicles (is_active, in_maintenance);

CREATE TABLE vehicle_maintenances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    performed_on DATE NOT NULL,
    completed_on DATE,
    odometer_km BIGINT NOT NULL,
    completion_odometer_km BIGINT,
    description TEXT NOT NULL,
    workshop VARCHAR(150) NOT NULL,
    cost NUMERIC(12, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_vehicle_maintenances_type CHECK (type IN ('PREVENTIVE', 'CORRECTIVE')),
    CONSTRAINT chk_vehicle_maintenances_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELED')),
    CONSTRAINT chk_vehicle_maintenances_description CHECK (btrim(description) <> ''),
    CONSTRAINT chk_vehicle_maintenances_workshop CHECK (btrim(workshop) <> ''),
    CONSTRAINT chk_vehicle_maintenances_odometer CHECK (odometer_km >= 0),
    CONSTRAINT chk_vehicle_maintenances_completion_odometer CHECK (
        completion_odometer_km IS NULL OR completion_odometer_km >= odometer_km
    ),
    CONSTRAINT chk_vehicle_maintenances_cost CHECK (cost >= 0),
    CONSTRAINT chk_vehicle_maintenances_dates CHECK (
        completed_on IS NULL OR completed_on >= performed_on
    ),
    CONSTRAINT chk_vehicle_maintenances_completion_state CHECK (
        (status = 'COMPLETED' AND completed_on IS NOT NULL)
        OR (status IN ('IN_PROGRESS', 'CANCELED'))
    )
);

CREATE UNIQUE INDEX ux_vehicle_maintenances_in_progress
    ON vehicle_maintenances (vehicle_id)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX ix_vehicle_maintenances_vehicle_history
    ON vehicle_maintenances (vehicle_id, performed_on DESC, created_at DESC);

CREATE TABLE preventive_maintenance_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    service_name VARCHAR(150) NOT NULL,
    notes TEXT,
    target_odometer_km BIGINT,
    target_date DATE,
    alert_lead_km BIGINT NOT NULL DEFAULT 1000,
    alert_lead_days INTEGER NOT NULL DEFAULT 15,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_maintenance_id UUID REFERENCES vehicle_maintenances (id),
    completed_on DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_preventive_schedules_service_name CHECK (btrim(service_name) <> ''),
    CONSTRAINT chk_preventive_schedules_targets CHECK (
        target_odometer_km IS NOT NULL OR target_date IS NOT NULL
    ),
    CONSTRAINT chk_preventive_schedules_target_km CHECK (
        target_odometer_km IS NULL OR target_odometer_km > 0
    ),
    CONSTRAINT chk_preventive_schedules_lead_km CHECK (alert_lead_km >= 0),
    CONSTRAINT chk_preventive_schedules_lead_days CHECK (alert_lead_days >= 0),
    CONSTRAINT chk_preventive_schedules_status CHECK (
        status IN ('PENDING', 'COMPLETED', 'CANCELED')
    )
);

CREATE INDEX ix_preventive_schedules_pending_vehicle
    ON preventive_maintenance_schedules (vehicle_id, status);
