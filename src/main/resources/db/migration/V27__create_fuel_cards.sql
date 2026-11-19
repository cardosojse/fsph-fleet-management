CREATE TABLE fuel_cards (
    id UUID PRIMARY KEY,
    card_number VARCHAR(50) NOT NULL,
    cardholder_name VARCHAR(120) NOT NULL,
    vehicle_id UUID REFERENCES vehicles(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    total_limit NUMERIC(12, 2) NOT NULL,
    available_balance NUMERIC(12, 2) NOT NULL,
    expiration_date DATE NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fuel_cards_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'CANCELED')),
    CONSTRAINT chk_fuel_cards_total_limit CHECK (total_limit >= 0),
    CONSTRAINT chk_fuel_cards_available_balance CHECK (available_balance >= 0)
);

CREATE UNIQUE INDEX idx_fuel_cards_card_number ON fuel_cards (LOWER(TRIM(card_number)));
CREATE UNIQUE INDEX idx_fuel_cards_vehicle_id ON fuel_cards (vehicle_id) WHERE vehicle_id IS NOT NULL;
CREATE INDEX idx_fuel_cards_status ON fuel_cards (status);
