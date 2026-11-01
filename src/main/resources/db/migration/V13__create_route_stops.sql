CREATE TABLE route_stops (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id UUID NOT NULL REFERENCES routes (id),
    transport_id UUID NOT NULL REFERENCES transports (id),
    stop_sequence INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_route_stops_sequence CHECK (stop_sequence > 0),
    CONSTRAINT chk_route_stops_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ux_route_stops_sequence UNIQUE (route_id, stop_sequence)
);
CREATE INDEX ix_route_stops_transport ON route_stops (transport_id, status);
