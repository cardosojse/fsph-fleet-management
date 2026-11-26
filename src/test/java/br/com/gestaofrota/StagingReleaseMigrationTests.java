package br.com.gestaofrota;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StagingReleaseMigrationTests extends PostgresIntegrationTest {
    @Test
    void migratesAnEmptyDatabaseWithoutDuplicateVersions() {
        String schema = schema();
        Flyway release = flyway(schema, null);
        assertThat(release.migrate().migrationsExecuted).isEqualTo(29); // Published history has no V11.
        release.validate();
        assertThat(release.info().current().getVersion().toString()).isEqualTo("30");
    }

    @Test
    void upgradesPublishedVersion25PreservingDataChecksumsAndCustomPermissions() {
        String schema = schema();
        assertThat(flyway(schema, "25").migrate().migrationsExecuted).isEqualTo(24);
        JdbcTemplate jdbc = jdbc(schema);
        UUID sector = UUID.randomUUID(), user = UUID.randomUUID(), driver = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID(), transport = UUID.randomUUID(), route = UUID.randomUUID();
        jdbc.update("INSERT INTO sectors (id, name) VALUES (?, 'Existing release sector')", sector);
        jdbc.update("INSERT INTO users (id, sector_id, name, email, password_hash, role) VALUES (?, ?, 'Existing driver', 'release@example.test', 'unchanged-hash', 'MOTORISTA')", user, sector);
        jdbc.update("INSERT INTO drivers (id, user_id) VALUES (?, ?)", driver, user);
        jdbc.update("INSERT INTO vehicles (id, plate, model, capacity, usage_type, current_odometer_km) VALUES (?, 'REL0029', 'Existing van', 8, 'Transporte', 123)", vehicle);
        jdbc.update("INSERT INTO transports (id, sector_id, requested_by_user_id, occurred_at, service_type, pickup_location, destination) VALUES (?, ?, ?, now(), 'Existing service', 'Origin', 'Destination')", transport, sector, user);
        jdbc.update("INSERT INTO routes (id, vehicle_id, driver_id, created_by_user_id, planned_start_at, planned_duration_minutes, planned_return_at) VALUES (?, ?, ?, ?, TIMESTAMPTZ '2026-12-01 12:00:00Z', 60, TIMESTAMPTZ '2026-12-01 13:00:00Z')", route, vehicle, driver, user);
        jdbc.update("INSERT INTO route_stops (route_id, transport_id, stop_sequence) VALUES (?, ?, 1)", route, transport);
        // A local-only fixture representing an administrator's customized matrix.
        jdbc.update("DELETE FROM role_permissions WHERE role = 'EMPLOYEE' AND permission <> 'SECTORS_READ'");

        List<String> tables = List.of("sectors", "users", "drivers", "vehicles", "transports", "routes", "route_stops");
        Map<String, Object> rows = new LinkedHashMap<>();
        for (String table : tables) rows.put(table, rows(jdbc, table));
        var history = jdbc.queryForList("SELECT version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank");
        var permissions = jdbc.queryForList("SELECT role, permission FROM role_permissions ORDER BY role, permission");

        Flyway release = flyway(schema, null);
        assertThat(release.migrate().migrationsExecuted).isEqualTo(5);
        release.validate();
        assertThat(release.info().current().getVersion().toString()).isEqualTo("30");
        for (String table : tables) assertThat(rows(jdbc, table)).as(table).isEqualTo(rows.get(table));
        assertThat(jdbc.queryForList("SELECT version, script, checksum, success FROM flyway_schema_history WHERE version IS NULL OR version::int <= 25 ORDER BY installed_rank"))
                .isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT role, permission FROM role_permissions ORDER BY role, permission"))
                .containsAll(permissions);
        assertThat(jdbc.queryForList("SELECT permission FROM role_permissions WHERE role = 'EMPLOYEE'", String.class))
                .containsExactly("SECTORS_READ");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE role = 'GESTOR_FROTA' AND permission = 'FUEL_CARDS_WRITE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions WHERE role = 'ADMIN' AND permission IN ('ROUTES_EXECUTE', 'SVO_REQUESTS_MANAGE', 'AUDIT_LOGS_READ')", Integer.class)).isEqualTo(3);
        for (String table : List.of("svo_teams", "svo_duty_shifts", "audit_logs", "fuel_cards", "svo_death_requests")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
    }

    private Object rows(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("SELECT to_jsonb(t)::text AS row FROM " + table + " t ORDER BY id");
    }

    private String schema() { return "release_" + UUID.randomUUID().toString().replace("-", ""); }

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(jdbcUrl(), databaseUsername(), databasePassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }

    private JdbcTemplate jdbc(String schema) {
        var dataSource = new DriverManagerDataSource(jdbcUrl(), databaseUsername(), databasePassword());
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        dataSource.setConnectionProperties(properties);
        return new JdbcTemplate(dataSource);
    }
}
