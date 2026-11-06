CREATE TABLE transports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sector_id UUID NOT NULL REFERENCES sectors (id),
    requested_by_user_id UUID NOT NULL REFERENCES users (id),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    service_type VARCHAR(100) NOT NULL,
    pickup_location VARCHAR(500) NOT NULL,
    destination VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_transports_status CHECK (status IN ('PENDING', 'ASSIGNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_transports_service CHECK (btrim(service_type) <> ''),
    CONSTRAINT chk_transports_pickup CHECK (btrim(pickup_location) <> ''),
    CONSTRAINT chk_transports_destination CHECK (btrim(destination) <> '')
);
CREATE INDEX ix_transports_sector ON transports (sector_id);
CREATE INDEX ix_transports_requester ON transports (requested_by_user_id, created_at DESC);
CREATE INDEX ix_transports_status_occurred ON transports (status, occurred_at);
