-- Preserve existing trips without inventing a duration. Active legacy trips must
-- be rescheduled or finished/cancelled before their resources can be reserved.
ALTER TABLE routes
    ADD COLUMN planned_duration_minutes INTEGER,
    ADD COLUMN planned_return_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT chk_routes_planned_schedule CHECK (
        (planned_duration_minutes IS NULL AND planned_return_at IS NULL)
        OR (planned_duration_minutes IS NOT NULL AND planned_return_at IS NOT NULL
            AND planned_duration_minutes > 0
            AND planned_return_at = planned_start_at + planned_duration_minutes * INTERVAL '1 minute')
    );

CREATE INDEX ix_routes_reserved_vehicle ON routes (vehicle_id, planned_start_at, planned_return_at)
    WHERE status IN ('PLANNED', 'IN_PROGRESS');
CREATE INDEX ix_routes_reserved_driver ON routes (driver_id, planned_start_at, planned_return_at)
    WHERE status IN ('PLANNED', 'IN_PROGRESS');
