CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    driver_id UUID NOT NULL REFERENCES drivers (id),
    created_by_user_id UUID NOT NULL REFERENCES users (id),
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    planned_start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    real_start_at TIMESTAMP WITH TIME ZONE,
    real_end_at TIMESTAMP WITH TIME ZONE,
    start_mileage BIGINT,
    end_mileage BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_routes_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_routes_mileage CHECK (
        (start_mileage IS NULL OR start_mileage >= 0)
        AND (end_mileage IS NULL OR (start_mileage IS NOT NULL AND end_mileage >= start_mileage))),
    CONSTRAINT chk_routes_execution CHECK (
        (status IN ('PLANNED', 'CANCELLED') AND real_start_at IS NULL AND start_mileage IS NULL
            AND real_end_at IS NULL AND end_mileage IS NULL)
        OR (status = 'IN_PROGRESS' AND real_start_at IS NOT NULL AND start_mileage IS NOT NULL
            AND real_end_at IS NULL AND end_mileage IS NULL)
        OR (status = 'COMPLETED' AND real_start_at IS NOT NULL AND start_mileage IS NOT NULL
            AND real_end_at IS NOT NULL AND end_mileage IS NOT NULL AND real_end_at >= real_start_at))
);
CREATE INDEX ix_routes_vehicle ON routes (vehicle_id, planned_start_at);
CREATE INDEX ix_routes_driver ON routes (driver_id, planned_start_at);
CREATE INDEX ix_routes_creator ON routes (created_by_user_id);
CREATE INDEX ix_routes_status_start ON routes (status, planned_start_at);
CREATE UNIQUE INDEX ux_routes_running_vehicle ON routes (vehicle_id) WHERE status = 'IN_PROGRESS';
CREATE UNIQUE INDEX ux_routes_running_driver ON routes (driver_id) WHERE status = 'IN_PROGRESS';
