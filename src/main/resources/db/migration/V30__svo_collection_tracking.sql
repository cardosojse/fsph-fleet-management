
ALTER TABLE svo_death_requests ADD COLUMN triage_sector_id UUID REFERENCES sectors(id);
UPDATE svo_death_requests r SET triage_sector_id=u.sector_id FROM users u WHERE u.id=r.triaged_by_user_id;
CREATE TABLE svo_collection_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), request_id UUID NOT NULL UNIQUE REFERENCES svo_death_requests(id),
    sector_id UUID NOT NULL REFERENCES sectors(id), driver_id UUID REFERENCES drivers(id), vehicle_id UUID REFERENCES vehicles(id),
    team_id UUID REFERENCES svo_teams(id), status VARCHAR(40) NOT NULL DEFAULT 'WAITING_ASSIGNMENT',
    requested_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    scheduled_at TIMESTAMPTZ, planned_return_at TIMESTAMPTZ, collection_deadline_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ, collected_at TIMESTAMPTZ, delivered_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_svo_order_status CHECK(status IN ('WAITING_ASSIGNMENT','ASSIGNED','IN_PROGRESS','COLLECTED','DELIVERED','CANCELED')),
    CONSTRAINT chk_svo_order_assignment CHECK(status IN ('WAITING_ASSIGNMENT','CANCELED') OR
        (driver_id IS NOT NULL AND vehicle_id IS NOT NULL AND scheduled_at IS NOT NULL AND planned_return_at>scheduled_at)),
    CONSTRAINT chk_svo_order_times CHECK((scheduled_at IS NULL OR scheduled_at>=requested_at)
        AND (collection_deadline_at IS NULL OR collection_deadline_at>=requested_at)
        AND (started_at IS NULL OR started_at>=requested_at)
        AND (collected_at IS NULL OR (started_at IS NOT NULL AND collected_at>=started_at))
        AND (delivered_at IS NULL OR (collected_at IS NOT NULL AND delivered_at>=collected_at))),
    CONSTRAINT chk_svo_order_stage_times CHECK(
        (status IN ('WAITING_ASSIGNMENT','ASSIGNED','CANCELED') AND started_at IS NULL AND collected_at IS NULL AND delivered_at IS NULL)
        OR (status='IN_PROGRESS' AND started_at IS NOT NULL AND collected_at IS NULL AND delivered_at IS NULL)
        OR (status='COLLECTED' AND collected_at IS NOT NULL AND delivered_at IS NULL)
        OR (status='DELIVERED' AND delivered_at IS NOT NULL))
);
CREATE INDEX ix_svo_orders_sector_requested ON svo_collection_orders(sector_id,requested_at DESC);
CREATE INDEX ix_svo_orders_resources ON svo_collection_orders(vehicle_id,driver_id,team_id) WHERE status IN ('ASSIGNED','IN_PROGRESS','COLLECTED');
CREATE TABLE svo_order_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES svo_collection_orders(id),
    author_id UUID NOT NULL REFERENCES users(id), author_name VARCHAR(255) NOT NULL, type VARCHAR(40) NOT NULL,
    text VARCHAR(2000), from_status VARCHAR(40), to_status VARCHAR(40) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
    order_version BIGINT NOT NULL, UNIQUE(order_id,order_version)
);
CREATE INDEX ix_svo_order_events_order ON svo_order_events(order_id,order_version);
CREATE FUNCTION prevent_svo_event_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'O histórico da coleta é imutável' USING ERRCODE='23514'; END $$;
CREATE TRIGGER svo_events_append_only BEFORE UPDATE OR DELETE ON svo_order_events FOR EACH ROW EXECUTE FUNCTION prevent_svo_event_change();

-- All allocation paths participate, including legacy routes, trips and duty shifts.
-- Advisory resource locks serialize concurrent checks inside the database transaction.
CREATE FUNCTION guard_svo_reservations() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rowdata jsonb:=to_jsonb(NEW); vid uuid; did uuid; tid uuid; oid uuid;
    reservation_start timestamptz; reservation_end timestamptz; resource_key text;
BEGIN
    IF TG_TABLE_NAME='svo_collection_orders' THEN
        IF NEW.status NOT IN ('ASSIGNED','IN_PROGRESS','COLLECTED') THEN RETURN NEW; END IF;
        vid:=NEW.vehicle_id;did:=NEW.driver_id;tid:=NEW.team_id;oid:=NEW.id;
        reservation_start:=LEAST(NEW.scheduled_at,NEW.started_at);
        reservation_end:=CASE WHEN NEW.status IN ('IN_PROGRESS','COLLECTED') THEN 'infinity'::timestamptz ELSE NEW.planned_return_at END;
    ELSIF TG_TABLE_NAME='routes' THEN
        IF NEW.status NOT IN ('PLANNED','IN_PROGRESS') THEN RETURN NEW; END IF;
        vid:=NEW.vehicle_id;did:=NEW.driver_id;reservation_start:=LEAST(NEW.planned_start_at,NEW.real_start_at);
        reservation_end:=CASE WHEN NEW.status='IN_PROGRESS' THEN 'infinity'::timestamptz ELSE COALESCE(NEW.planned_return_at,'infinity'::timestamptz) END;
    ELSIF TG_TABLE_NAME='trips' THEN
        IF NEW.status<>'OPEN' THEN RETURN NEW; END IF;
        vid:=NEW.vehicle_id;did:=NEW.driver_id;reservation_start:=NEW.started_at;reservation_end:='infinity'::timestamptz;
    ELSIF TG_TABLE_NAME='trip_requests' THEN
        IF NEW.status NOT IN ('APPROVED','SCHEDULED') THEN RETURN NEW; END IF;
        vid:=COALESCE(NEW.assigned_vehicle_id,NEW.requested_vehicle_id);did:=NEW.assigned_driver_id;
        reservation_start:=NEW.departure_at;reservation_end:=NEW.expected_return_at;
    ELSE
        IF NEW.status NOT IN ('SCHEDULED','ACTIVE') THEN RETURN NEW; END IF;
        vid:=NEW.vehicle_id;tid:=NEW.team_id;reservation_start:=NEW.start_at;reservation_end:=NEW.end_at;
    END IF;
    FOR resource_key IN SELECT k FROM unnest(ARRAY['vehicle:'||vid::text,'driver:'||did::text,'team:'||tid::text]) k WHERE k IS NOT NULL ORDER BY k
    LOOP PERFORM pg_advisory_xact_lock(hashtextextended(resource_key,0)); END LOOP;
    IF EXISTS(SELECT 1 FROM svo_collection_orders o WHERE o.status IN ('ASSIGNED','IN_PROGRESS','COLLECTED')
        AND (oid IS NULL OR o.id<>oid)
        AND (o.vehicle_id=vid OR o.driver_id=did OR (TG_TABLE_NAME<>'svo_duty_shifts' AND o.team_id=tid))
        AND LEAST(o.scheduled_at,o.started_at)<reservation_end
        AND (CASE WHEN o.status IN ('IN_PROGRESS','COLLECTED') THEN 'infinity'::timestamptz ELSE o.planned_return_at END)>reservation_start
        AND (TG_TABLE_NAME<>'svo_duty_shifts' OR o.team_id IS DISTINCT FROM tid)) THEN
        RAISE EXCEPTION 'Recurso reservado para outra coleta SVO' USING ERRCODE='23514';
    END IF;
    IF TG_TABLE_NAME='svo_collection_orders' THEN
        IF EXISTS(SELECT 1 FROM routes r WHERE r.status IN ('PLANNED','IN_PROGRESS') AND (r.vehicle_id=vid OR r.driver_id=did)
            AND LEAST(r.planned_start_at,r.real_start_at)<reservation_end
            AND (CASE WHEN r.status='IN_PROGRESS' THEN 'infinity'::timestamptz ELSE COALESCE(r.planned_return_at,'infinity'::timestamptz) END)>reservation_start)
        OR EXISTS(SELECT 1 FROM trips t WHERE t.status='OPEN' AND (t.vehicle_id=vid OR t.driver_id=did))
        OR EXISTS(SELECT 1 FROM trip_requests t WHERE t.status IN ('APPROVED','SCHEDULED')
            AND (COALESCE(t.assigned_vehicle_id,t.requested_vehicle_id)=vid OR t.assigned_driver_id=did)
            AND t.departure_at<reservation_end AND t.expected_return_at>reservation_start)
        OR EXISTS(SELECT 1 FROM svo_duty_shifts s WHERE s.status IN ('SCHEDULED','ACTIVE') AND s.vehicle_id=vid
            AND s.team_id IS DISTINCT FROM tid AND s.start_at<reservation_end AND s.end_at>reservation_start) THEN
            RAISE EXCEPTION 'Veículo ou motorista possui viagem, manutenção ou plantão conflitante' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER svo_order_reservation BEFORE INSERT OR UPDATE ON svo_collection_orders FOR EACH ROW EXECUTE FUNCTION guard_svo_reservations();
CREATE TRIGGER route_svo_reservation BEFORE INSERT OR UPDATE ON routes FOR EACH ROW EXECUTE FUNCTION guard_svo_reservations();
CREATE TRIGGER trip_svo_reservation BEFORE INSERT OR UPDATE ON trips FOR EACH ROW EXECUTE FUNCTION guard_svo_reservations();
CREATE TRIGGER request_svo_reservation BEFORE INSERT OR UPDATE ON trip_requests FOR EACH ROW EXECUTE FUNCTION guard_svo_reservations();
CREATE TRIGGER shift_svo_reservation BEFORE INSERT OR UPDATE ON svo_duty_shifts FOR EACH ROW EXECUTE FUNCTION guard_svo_reservations();

ALTER TABLE role_permissions DROP CONSTRAINT chk_role_permissions_permission;
ALTER TABLE role_permissions ADD CONSTRAINT chk_role_permissions_permission CHECK(permission IN ('USERS_READ', 'USERS_WRITE', 'SECTORS_READ', 'SECTORS_WRITE', 'PERMISSIONS_READ', 'PERMISSIONS_WRITE', 'VEHICLES_READ', 'VEHICLES_WRITE', 'VEHICLES_ASSIGNED_READ', 'TRIPS_WRITE', 'TRIPS_READ', 'DRIVERS_READ', 'DRIVERS_WRITE', 'MAINTENANCES_READ', 'MAINTENANCES_WRITE', 'FUEL_STATIONS_READ', 'FUEL_STATIONS_WRITE', 'FUELINGS_READ', 'FUELINGS_WRITE', 'TRIP_REQUESTS_READ', 'TRIP_REQUESTS_WRITE', 'TRIP_REQUESTS_MANAGE', 'NOTIFICATIONS_READ', 'TRANSPORTS_READ', 'TRANSPORTS_REQUEST', 'TRANSPORTS_MANAGE', 'ROUTES_READ', 'ROUTES_MANAGE', 'ROUTES_EXECUTE', 'SVO_TEAMS_READ', 'SVO_TEAMS_WRITE', 'SVO_SHIFTS_READ', 'SVO_SHIFTS_WRITE', 'AUDIT_LOGS_READ', 'FUEL_CARDS_READ', 'FUEL_CARDS_WRITE', 'SVO_REQUESTS_READ', 'SVO_REQUESTS_WRITE', 'SVO_REQUESTS_MANAGE', 'SVO_ORDERS_READ', 'SVO_ORDERS_MANAGE', 'SVO_ORDERS_EXECUTE', 'SVO_ORDERS_NOTES', 'SVO_ORDERS_REPORT'));

-- Extend untouched default matrices only; custom access decisions remain intact.
WITH defaults(role,permissions) AS (VALUES
('MOTORISTA', ARRAY['SECTORS_READ','VEHICLES_ASSIGNED_READ','FUEL_STATIONS_READ','FUELINGS_WRITE','NOTIFICATIONS_READ','TRIPS_WRITE','ROUTES_READ','ROUTES_EXECUTE']),
('ATENDENTE_SVO', ARRAY['SECTORS_READ','SVO_TEAMS_READ','SVO_SHIFTS_READ','SVO_REQUESTS_READ','SVO_REQUESTS_WRITE','SVO_REQUESTS_MANAGE']),
('GESTOR_SVO', ARRAY['SECTORS_READ','SVO_TEAMS_READ','SVO_TEAMS_WRITE','SVO_SHIFTS_READ','SVO_SHIFTS_WRITE','SVO_REQUESTS_READ','SVO_REQUESTS_WRITE','SVO_REQUESTS_MANAGE']),
('EQUIPE_COLETA_SVO', ARRAY['SECTORS_READ','SVO_TEAMS_READ','SVO_SHIFTS_READ']),
('GESTOR_FROTA', ARRAY['SECTORS_READ','VEHICLES_READ','VEHICLES_WRITE','DRIVERS_READ','DRIVERS_WRITE','MAINTENANCES_READ','MAINTENANCES_WRITE','FUEL_STATIONS_READ','FUEL_STATIONS_WRITE','FUELINGS_READ','FUELINGS_WRITE','TRIP_REQUESTS_READ','TRIP_REQUESTS_WRITE','TRIP_REQUESTS_MANAGE','NOTIFICATIONS_READ','TRIPS_READ','TRANSPORTS_READ','TRANSPORTS_REQUEST','TRANSPORTS_MANAGE','ROUTES_READ','ROUTES_MANAGE','SVO_TEAMS_READ','SVO_TEAMS_WRITE','SVO_SHIFTS_READ','SVO_SHIFTS_WRITE','FUEL_CARDS_READ','FUEL_CARDS_WRITE'])
), default_roles AS (
 SELECT rp.role FROM role_permissions rp JOIN defaults d ON d.role=rp.role GROUP BY rp.role,d.permissions
 HAVING count(*)=cardinality(d.permissions) AND bool_and(rp.permission=ANY(d.permissions))
)
INSERT INTO role_permissions(role,permission) SELECT g.role,g.permission FROM (VALUES
('ADMIN','SVO_ORDERS_READ'),
('ADMIN','SVO_ORDERS_MANAGE'),
('ADMIN','SVO_ORDERS_EXECUTE'),
('ADMIN','SVO_ORDERS_NOTES'),
('ADMIN','SVO_ORDERS_REPORT'),
('MOTORISTA','SVO_ORDERS_READ'),
('MOTORISTA','SVO_ORDERS_EXECUTE'),
('EQUIPE_COLETA_SVO','SVO_ORDERS_READ'),
('EQUIPE_COLETA_SVO','SVO_ORDERS_EXECUTE'),
('ATENDENTE_SVO','SVO_ORDERS_READ'),
('ATENDENTE_SVO','SVO_ORDERS_NOTES'),
('GESTOR_SVO','SVO_ORDERS_READ'),
('GESTOR_SVO','SVO_ORDERS_MANAGE'),
('GESTOR_SVO','SVO_ORDERS_NOTES'),
('GESTOR_SVO','SVO_ORDERS_REPORT'),
('GESTOR_FROTA','SVO_ORDERS_READ'),
('GESTOR_FROTA','SVO_ORDERS_MANAGE'),
('GESTOR_FROTA','SVO_ORDERS_REPORT')
) g(role,permission) WHERE g.role='ADMIN' OR g.role IN(SELECT role FROM default_roles) ON CONFLICT DO NOTHING;
