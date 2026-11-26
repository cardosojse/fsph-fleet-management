package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SvoFleetPanelsReportIntegrationTests extends PostgresIntegrationTest {
    private static final String BASE = "/api/v1/reports/svo/";
    private static final Set<Permission> ALL = Set.of(Permission.FUELINGS_READ, Permission.MAINTENANCES_READ);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test
    @Transactional
    void matchesOperationalOrderFieldsAndExportsTheStoredResults() throws Exception {
        var actor = principal(ALL);
        db.update("""
                INSERT INTO vehicles (plate, model, capacity, usage_type, current_odometer_km)
                SELECT '000300' || i, 'Vehicle ' || i, 4, 'Teste', 100
                FROM generate_series(1, 6) i
                """);
        db.update("""
                INSERT INTO prime_fuel_stations (name, cnpj, city, state, address)
                VALUES ('SVO stage 3', '00000000003000', 'Aracaju', 'SE', 'Teste')
                """);
        db.update("""
                INSERT INTO fuelings (vehicle_id, station_id, registered_by_user_id, fueled_on,
                    odometer_km, distance_driven_km, liters, total_value, fuel_type, created_at)
                SELECT v.id, s.id, u.id, CURRENT_DATE - (i / 3), 100, 123, 10, 76.50, 'DIESEL',
                    now() + i * interval '1 second'
                FROM vehicles v CROSS JOIN prime_fuel_stations s CROSS JOIN users u
                CROSS JOIN generate_series(1, 6) i
                WHERE v.plate = '000300' || i AND s.cnpj = '00000000003000' AND u.email = 'admin.test@exemplo.com'
                """);
        db.update("""
                INSERT INTO preventive_maintenance_schedules (vehicle_id, service_name, notes, target_odometer_km,
                    target_date, alert_lead_km, alert_lead_days)
                SELECT v.id, fixture.service, fixture.notes, fixture.km, fixture.day, 10, 3
                FROM vehicles v CROSS JOIN (VALUES
                    ('Revisao vencida', 'Manter registro', NULL::bigint, CURRENT_DATE - 1),
                    ('Revisao por km', NULL, 105::bigint, NULL::date),
                    ('Revisao futura', NULL, 1000::bigint, CURRENT_DATE + 60)
                ) fixture(service, notes, km, day)
                WHERE v.plate = '0003001'
                """);

        String consumption = read(BASE + "vehicle-consumption", actor);
        List<Map<String, Object>> sourceVehicles = JsonPath.read(read("/api/v1/fuelings/consumption", actor), "$.vehicles");
        List<List<String>> rows = JsonPath.read(consumption, "$.rows");
        assertEquals(sourceVehicles.stream().limit(5).map(v -> v.get("vehiclePlate")).toList(),
                rows.stream().map(List::getFirst).toList());
        for (int i = 0; i < rows.size(); i++) {
            var source = sourceVehicles.get(i);
            assertEquals(source.get("totalMeasuredDistanceKm").toString(), rows.get(i).get(1));
            assertEquals(decimal(source.get("averageKmPerLiter")), rows.get(i).get(2));
        }
        var measured = sourceVehicles.stream().filter(v -> "0003001".equals(v.get("vehiclePlate")))
                .findFirst().orElseThrow();
        assertEquals("12,30", decimal(measured.get("averageKmPerLiter")));

        String fuelings = read(BASE + "recent-fuelings", actor);
        List<Map<String, Object>> recent = JsonPath.read(read("/api/v1/fuelings", actor), "$");
        rows = JsonPath.read(fuelings, "$.rows");
        assertEquals(5, rows.size());
        assertEquals(recent.stream().limit(5).map(v -> v.get("vehiclePlate")).toList(),
                rows.stream().map(List::getFirst).toList());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(decimal(recent.get(i).get("liters")), rows.get(i).get(2));
            assertEquals(decimal(recent.get(i).get("totalValue")), rows.get(i).get(3));
        }

        String alerts = read(BASE + "maintenance-alerts", actor);
        List<Map<String, Object>> sourceAlerts = JsonPath.read(read("/api/v1/maintenance-schedules/alerts", actor), "$");
        rows = JsonPath.read(alerts, "$.rows");
        assertEquals(sourceAlerts.stream().map(v -> v.get("vehiclePlate") + " — " + v.get("serviceName")).toList(),
                rows.stream().map(List::getFirst).toList());
        assertTrue(rows.stream().anyMatch(row -> row.get(1).contains("Manter registro")));
        assertTrue(rows.stream().anyMatch(row -> row.get(1).equals("Odômetro previsto: 105 km")));
        assertFalse(rows.stream().anyMatch(row -> row.getFirst().contains("Revisao futura")));

        db.update("UPDATE fuelings SET total_value = 879.10 WHERE vehicle_id IN (SELECT id FROM vehicles WHERE plate LIKE '000300%')");
        db.update("UPDATE preventive_maintenance_schedules SET notes = 'Notas alteradas' WHERE service_name = 'Revisao vencida'");
        for (var entry : Map.of("vehicle-consumption", consumption, "recent-fuelings", fuelings,
                "maintenance-alerts", alerts).entrySet()) {
            String snapshotId = JsonPath.read(entry.getValue(), "$.snapshotId");
            String reference = JsonPath.read(entry.getValue(), "$.referenceLabel");
            List<List<String>> storedRows = JsonPath.read(entry.getValue(), "$.rows");
            var pdf = bytes(entry.getKey(), "pdf", snapshotId, actor);
            try (var doc = Loader.loadPDF(pdf)) {
                String text = new PDFTextStripper().getText(doc);
                assertTrue(text.contains(reference));
                assertFalse(text.contains("879,10"));
                assertFalse(text.contains("Notas alteradas"));
            }
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes(entry.getKey(), "excel", snapshotId, actor)))) {
                var sheet = book.getSheetAt(0);
                assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains(reference));
                for (int i = 0; i < storedRows.size(); i++) {
                    assertEquals(storedRows.get(i).getFirst(), sheet.getRow(5+i).getCell(0).getStringCellValue());
                    if (entry.getKey().equals("recent-fuelings")) {
                        var value = sheet.getRow(5+i).getCell(3);
                        assertEquals(new BigDecimal(storedRows.get(i).get(3).replace(',', '.')).doubleValue(),
                                value.getNumericCellValue());
                        assertTrue(value.getCellStyle().getDataFormatString().contains("R$"));
                    }
                }
            }
            mvc.perform(get(BASE + entry.getKey() + "/pdf").param("snapshotId", snapshotId)
                    .with(user(principal(ALL)))).andExpect(status().isNotFound());
        }
    }

    @Test
    void checksOperationalPermissionsForEachRepresentation() throws Exception {
        for (String kind : List.of("vehicle-consumption", "recent-fuelings", "maintenance-alerts")) {
            var wrong = kind.equals("maintenance-alerts") ? Permission.FUELINGS_READ : Permission.MAINTENANCES_READ;
            for (String suffix : List.of("", "/pdf", "/excel")) {
                var path = BASE + kind + suffix;
                mvc.perform(get(path).param("snapshotId", UUID.randomUUID().toString()))
                        .andExpect(status().isUnauthorized());
                mvc.perform(get(path).param("snapshotId", UUID.randomUUID().toString())
                        .with(user(principal(Set.of(wrong))))).andExpect(status().isForbidden());
            }
        }
    }

    private String decimal(Object value) {
        return value == null ? "\u2014" : new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    private String read(String path, AuthenticatedUser actor) throws Exception {
        return mvc.perform(get(path).with(user(actor))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private byte[] bytes(String kind, String format, String snapshot, AuthenticatedUser actor) throws Exception {
        return mvc.perform(get(BASE + kind + "/" + format).param("snapshotId", snapshot).with(user(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private AuthenticatedUser principal(Set<Permission> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "Teste", "svo3@example.test",
                "hash", UserRole.GESTOR_FROTA, true, permissions);
    }
}
