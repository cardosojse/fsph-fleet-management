\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE sectors (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(100) NOT NULL);
CREATE UNIQUE INDEX sectors_test_name ON sectors (lower(name));
CREATE TEMP TABLE legacy_users (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), sector_id UUID REFERENCES sectors(id));
INSERT INTO sectors(name) VALUES (' Administração '), ('LACEN'), ('Superintendência do Hemose'), ('Serviço de Verificação de Óbitos'), ('Coordenação Infraestrutura'), ('Outro setor');
INSERT INTO legacy_users(sector_id) SELECT id FROM sectors WHERE name = 'LACEN';
INSERT INTO legacy_users(sector_id) VALUES (NULL);
CREATE TEMP TABLE before_assignment AS SELECT * FROM legacy_users;
\ir ../../../main/resources/db/migration/V25__seed_institutional_sectors.sql
\ir ../../../main/resources/db/migration/V25__seed_institutional_sectors.sql
DO $$ BEGIN
    IF (SELECT count(*) FROM sectors) <> 6 THEN
        RAISE EXCEPTION 'Equivalent units were duplicated';
    END IF;
    IF EXISTS ((SELECT * FROM legacy_users) EXCEPT (SELECT * FROM before_assignment)) THEN
        RAISE EXCEPTION 'Existing assignments were modified';
    END IF;
END $$;
DELETE FROM sectors WHERE name = 'Superintendência do Hemose';
\ir ../../../main/resources/db/migration/V25__seed_institutional_sectors.sql
DO $$ BEGIN
    IF (SELECT count(*) FROM sectors) <> 6 OR NOT EXISTS (SELECT 1 FROM sectors WHERE name = 'Superintendência Hemose') THEN
        RAISE EXCEPTION 'Missing institutional unit was not added';
    END IF;
END $$;
ROLLBACK;
