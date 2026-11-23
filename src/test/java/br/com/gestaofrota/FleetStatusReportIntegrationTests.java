package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
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
class FleetStatusReportIntegrationTests extends PostgresIntegrationTest {
    private static final String BASE = "/api/v1/reports/svo/";
    private static final Set<Permission> READ = Set.of(Permission.VEHICLES_READ, Permission.TRIPS_READ);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    void matchesSourceDataAndExportsTheOriginalSnapshot() throws Exception {
        var manager = principal(UserRole.GESTOR_FROTA, READ);
        db.update("""
                INSERT INTO vehicles (plate, model, capacity, usage_type, current_odometer_km, is_active, in_maintenance)
                VALUES ('SVO0001', 'Synthetic', 4, 'Teste', 100, true, false),
                       ('SVO0002', 'Synthetic', 4, 'Teste', 100, true, false),
                       ('SVO0003', 'Synthetic', 4, 'Teste', 100, true, true),
                       ('SVO0004', 'Synthetic', 4, 'Teste', 100, false, false)
                """);
        db.update("INSERT INTO drivers (user_id) SELECT id FROM users WHERE email = 'admin.test@exemplo.com' ON CONFLICT DO NOTHING");
        db.update("""
                INSERT INTO trips (vehicle_id, driver_id, started_at, initial_odometer_km, status)
                SELECT v.id, d.id, now(), 100, 'OPEN' FROM vehicles v CROSS JOIN drivers d
                JOIN users u ON u.id = d.user_id
                WHERE v.plate IN ('SVO0002', 'SVO0003', 'SVO0004') AND u.email = 'admin.test@exemplo.com'
                """);
        var vehiclesJson = mvc.perform(get("/api/v1/vehicles").with(user(manager)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> fleet = JsonPath.read(vehiclesJson, "$");
        var open = new HashSet<>();
        int totalPages;
        int page = 0;
        do {
            var tripsJson = mvc.perform(get("/api/v1/trips").param("page", Integer.toString(page))
                            .param("size", "100").with(user(manager)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            totalPages = JsonPath.read(tripsJson, "$.totalPages");
            List<Map<String, Object>> trips = JsonPath.read(tripsJson, "$.content");
            trips.stream().filter(trip -> "OPEN".equals(trip.get("status")))
                    .map(trip -> trip.get("vehicleId")).forEach(open::add);
        } while (++page < totalPages);
        for (String plate : List.of("SVO0002", "SVO0003", "SVO0004")) {
            var vehicle = fleet.stream().filter(item -> plate.equals(item.get("plate"))).findFirst().orElseThrow();
            assertTrue(open.contains(vehicle.get("id")));
        }
        var active = fleet.stream().filter(vehicle -> Boolean.TRUE.equals(vehicle.get("active"))).toList();
        var maintenance = active.stream().filter(vehicle -> Boolean.TRUE.equals(vehicle.get("inMaintenance"))).count();
        var onRoute = active.stream().filter(vehicle -> !Boolean.TRUE.equals(vehicle.get("inMaintenance"))
                && open.contains(vehicle.get("id"))).count();
        var available = active.stream().filter(vehicle -> !Boolean.TRUE.equals(vehicle.get("inMaintenance"))
                && Boolean.TRUE.equals(vehicle.get("available")) && !open.contains(vehicle.get("id"))).count();
        var expected = List.of(available, onRoute, maintenance, active.size() - available - onRoute - maintenance);
        int totalActive = active.size();
        var percentage = BigDecimal.valueOf(available * 100)
                .divide(BigDecimal.valueOf(totalActive), 2, RoundingMode.HALF_UP);
        String formattedPercentage = percentage.toPlainString().replace('.', ',') + "%";
        for (String indicator : List.of("availability", "fleet-status")) {
            var base = BASE + indicator;
            var view = mvc.perform(get(base).with(user(manager))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalActive").value(totalActive))
                    .andExpect(jsonPath("$.rows[0].percentage").value(percentage.doubleValue()))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            List<Integer> counts = JsonPath.read(view, "$.rows[*].count");
            assertEquals(expected, counts.stream().map(Integer::longValue).toList());
            String snapshot = JsonPath.read(view, "$.snapshotId");
            String reference = JsonPath.read(view, "$.referenceLabel");
            String title = JsonPath.read(view, "$.title");
            db.update("UPDATE vehicles SET is_active = false WHERE plate = 'SVO0001'");
            entityManager.clear();
            mvc.perform(get(base).with(user(manager))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalActive").value(totalActive - 1));
            byte[] pdf = mvc.perform(get(base + "/pdf").param("snapshotId", snapshot).with(user(manager)))
                    .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                    .andReturn().getResponse().getContentAsByteArray();
            try (var document = Loader.loadPDF(pdf)) {
                var text = new PDFTextStripper().getText(document);
                assertTrue(text.contains(title));
                assertTrue(text.contains("FSPH"));
                assertTrue(text.contains("SVO | Tipo: Individual"));
                assertTrue(text.contains(reference));
                assertTrue(text.contains("Total da frota ativa: " + totalActive));
                assertTrue(text.contains(formattedPercentage));
            }
            byte[] xlsx = mvc.perform(get(base + "/excel").param("snapshotId", snapshot).with(user(manager)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
                var sheet = workbook.getSheetAt(0);
                assertEquals(title, sheet.getRow(1).getCell(0).getStringCellValue());
                assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains(reference));
                assertEquals((double) available, sheet.getRow(5).getCell(1).getNumericCellValue());
                var percentCell = sheet.getRow(5).getCell(2);
                assertEquals(percentage.movePointLeft(2).doubleValue(), percentCell.getNumericCellValue());
                assertEquals("0.00%", percentCell.getCellStyle().getDataFormatString());
                assertEquals(4, sheet.getNumMergedRegions());
                assertEquals(1, sheet.getPrintSetup().getFitWidth());
            }
            mvc.perform(get(base + "/pdf").param("snapshotId", snapshot)
                    .with(user(principal(UserRole.GESTOR_FROTA, READ)))).andExpect(status().isNotFound());
            db.update("UPDATE vehicles SET is_active = true WHERE plate = 'SVO0001'");
            entityManager.clear();
        }
    }

    @Test
    void requiresBothSourcePermissionsAcrossAllFormats() throws Exception {
        for (String suffix : List.of("", "/pdf", "/excel")) {
            var path = BASE + "fleet-status" + suffix;
            mvc.perform(get(path).param("snapshotId", UUID.randomUUID().toString()))
                    .andExpect(status().isUnauthorized());
            for (var permission : READ) {
                mvc.perform(get(path).param("snapshotId", UUID.randomUUID().toString())
                        .with(user(principal(UserRole.GESTOR_FROTA, Set.of(permission)))))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(get(path).param("snapshotId", UUID.randomUUID().toString())
                    .with(user(principal(UserRole.MOTORISTA, READ)))).andExpect(status().isForbidden());
        }
    }

    private AuthenticatedUser principal(UserRole role, Set<Permission> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "Teste", "svo@example.test",
                "hash", role, true, permissions);
    }
}
