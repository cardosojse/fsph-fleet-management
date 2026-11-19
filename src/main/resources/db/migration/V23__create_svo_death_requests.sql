CREATE TABLE svo_death_requests (
    id UUID PRIMARY KEY,
    protocol_number VARCHAR(30) NOT NULL,
    requester_user_id UUID NOT NULL REFERENCES users(id),
    deceased_name VARCHAR(150),
    deceased_age INTEGER,
    deceased_gender VARCHAR(20),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    death_location_type VARCHAR(50) NOT NULL,
    address VARCHAR(250) NOT NULL,
    neighborhood VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL DEFAULT 'Aracaju',
    reference_point VARCHAR(250),
    contact_name VARCHAR(150) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    relationship_with_deceased VARCHAR(100),
    presumed_circumstance VARCHAR(500),
    has_suspected_violence BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_TRIAGE',
    rejection_reason VARCHAR(500),
    triaged_by_user_id UUID REFERENCES users(id),
    triaged_at TIMESTAMP WITH TIME ZONE,
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_svo_death_requests_status CHECK (status IN (
        'PENDING_TRIAGE', 'TRIAGED', 'REJECTED', 'CANCELED', 'ORDER_GENERATED'
    )),
    CONSTRAINT chk_svo_death_requests_location CHECK (death_location_type IN (
        'DOMICILE', 'PUBLIC_WAY', 'HOSPITAL', 'HEALTH_POST', 'OTHER'
    ))
);

CREATE UNIQUE INDEX idx_svo_death_requests_protocol ON svo_death_requests (protocol_number);
CREATE INDEX idx_svo_death_requests_requester ON svo_death_requests (requester_user_id);
CREATE INDEX idx_svo_death_requests_status ON svo_death_requests (status);
CREATE INDEX idx_svo_death_requests_created_at ON svo_death_requests (created_at DESC);
