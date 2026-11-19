CREATE TABLE svo_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_svo_teams_name CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX ux_svo_teams_name ON svo_teams (upper(btrim(name)));

CREATE TABLE svo_team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES svo_teams (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id),
    role_in_team VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_svo_team_members_role CHECK (btrim(role_in_team) <> '')
);

CREATE UNIQUE INDEX ux_svo_team_members_active ON svo_team_members (team_id, user_id) WHERE is_active = TRUE;
CREATE INDEX ix_svo_team_members_team ON svo_team_members (team_id);
CREATE INDEX ix_svo_team_members_user ON svo_team_members (user_id);

CREATE TABLE svo_duty_shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES svo_teams (id),
    vehicle_id UUID REFERENCES vehicles (id),
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    shift_type VARCHAR(50),
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_svo_duty_shifts_period CHECK (end_at > start_at),
    CONSTRAINT chk_svo_duty_shifts_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELED'))
);

CREATE INDEX ix_svo_duty_shifts_period ON svo_duty_shifts (start_at, end_at);
CREATE INDEX ix_svo_duty_shifts_team_period ON svo_duty_shifts (team_id, start_at, end_at);
CREATE INDEX ix_svo_duty_shifts_vehicle_period ON svo_duty_shifts (vehicle_id, start_at, end_at) WHERE vehicle_id IS NOT NULL;

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    actor_user_id UUID REFERENCES users (id),
    actor_name VARCHAR(150),
    actor_email VARCHAR(255),
    actor_role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    details TEXT,
    client_ip VARCHAR(50),
    CONSTRAINT chk_audit_logs_action CHECK (btrim(action) <> ''),
    CONSTRAINT chk_audit_logs_resource_type CHECK (btrim(resource_type) <> '')
);

CREATE INDEX ix_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_action ON audit_logs (action, occurred_at DESC);
