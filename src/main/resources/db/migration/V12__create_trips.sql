CREATE TABLE trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    driver_id UUID NOT NULL REFERENCES drivers (id),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    initial_odometer_km BIGINT NOT NULL,
    final_odometer_km BIGINT,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT chk_trips_initial_odometer CHECK (initial_odometer_km >= 0),
    CONSTRAINT chk_trips_final_odometer CHECK (
        final_odometer_km IS NULL OR final_odometer_km >= initial_odometer_km
    ),
    CONSTRAINT chk_trips_times CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT chk_trips_status CHECK (
        (status = 'OPEN' AND ended_at IS NULL AND final_odometer_km IS NULL)
        OR (status = 'COMPLETED' AND ended_at IS NOT NULL AND final_odometer_km IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_trips_open_vehicle
    ON trips (vehicle_id)
    WHERE status = 'OPEN';

CREATE INDEX ix_trips_vehicle_history
    ON trips (vehicle_id, started_at DESC);

CREATE INDEX ix_trips_driver_history
    ON trips (driver_id, started_at DESC);
