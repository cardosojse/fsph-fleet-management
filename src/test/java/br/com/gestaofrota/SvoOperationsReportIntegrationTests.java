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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SvoOperationsReportIntegrationTests extends PostgresIntegrationTest {
    private static final String BASE = "/api/v1/reports/svo/";
    private static final Set<Permission> ALL = Set.of(Permission.TRIP_REQUESTS_READ, Permission.TRIPS_READ,
            Permission.VEHICLES_READ, Permission.DRIVERS_READ, Permission.SECTORS_READ);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @Test
    @Transactional
    void usesSourceRowsAndAllTripPagesAndExportsTheStoredFiltersAndRows() throws Exception {
        var principal = principal(UserRole.GESTOR_FROTA, ALL);
        var sector = db.queryForObject("INSERT INTO sectors (name) VALUES ('SVO etapa 2') RETURNING id", UUID.class);
        db.update("""
                INSERT INTO vehicles (sector_id, plate, model, capacity, usage_type, current_odometer_km)
                VALUES (?, 'SVO2201', 'Van de teste', 4, 'Teste', 100),
                       (?, 'SVO2202', 'Outro veículo', 4, 'Teste', 100)
                """, sector, null);
        var admin = db.queryForObject("SELECT id FROM users WHERE email = 'admin.test@exemplo.com'", UUID.class);
        db.update("INSERT INTO drivers (user_id) VALUES (?) ON CONFLICT DO NOTHING", admin);
        var driver = db.queryForObject("SELECT id FROM drivers WHERE user_id = ?", UUID.class, admin);
        db.update("""
                INSERT INTO trips (vehicle_id, driver_id, started_at, initial_odometer_km, status)
                SELECT id, ?, now() - interval '8 days', 100, 'OPEN' FROM vehicles WHERE plate = 'SVO2201'
                """, driver);
        db.update("""
                INSERT INTO trips (vehicle_id, driver_id, started_at, ended_at, initial_odometer_km, final_odometer_km, status)
                SELECT v.id, ?, now() - interval '1 hour', now(), 100, 101, 'COMPLETED'
                FROM vehicles v CROSS JOIN generate_series(1, 105) WHERE v.plate = 'SVO2202'
                """, driver);
        var source = mvc.perform(get("/api/v1/trips").param("size", "100").with(user(principal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(((Number) JsonPath.read(source, "$.totalPages")).intValue() >= 2);

        for (String period : List.of("today", "week")) {
            mvc.perform(get(BASE + "ongoing-trips").param("sectorId", sector.toString())
                    .param("period", period).with(user(principal)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.rows").isEmpty());
        }
        mvc.perform(get(BASE + "ongoing-trips").param("sectorId", sector.toString()).with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows").isEmpty());
        var view = mvc.perform(get(BASE + "ongoing-trips").param("sectorId", sector.toString())
                        .param("period", "all").with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][1]").value("SVO etapa 2"))
                .andExpect(jsonPath("$.rows[0][3]").value("Van de teste • SVO2201"))
                .andExpect(jsonPath("$.rows[0][5]").value("Em execução"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var baseline = query("triage", principal);
        db.update("""
                INSERT INTO trip_requests (requester_user_id, departure_at, expected_return_at, origin, destination,
                                           purpose, passenger_count, status, created_at)
                VALUES (?, now(), now() + interval '1 hour', 'Origem', 'Destino', 'Teste', 1, 'PENDING', now()),
                       (?, now(), now() + interval '1 hour', 'Origem', 'Destino', 'Teste', 1, 'APPROVED', now() - interval '1 day')
                """, admin, admin);
        var triage = query("triage", principal);
        var daily = query("daily-requests", principal);
        assertEquals(count(baseline, 0) + 1, count(triage, 0));
        assertEquals(count(baseline, 1) + 1, count(triage, 1));
        assertEquals(count(baseline, 2) + 1, count(triage, 2));
        assertEquals(count(triage, 2), count(daily, 6));
        assertEquals(7, ((List<?>) JsonPath.read(daily, "$.rows")).size());
        assertTrue(count(triage, 3) >= 1);

        db.update("UPDATE vehicles SET model = 'Modelo alterado' WHERE plate = 'SVO2201'");
        for (String[] report : new String[][] {
            {"ongoing-trips", view, "SVO2201"}, {"triage", triage, "Triagens pendentes"},
            {"daily-requests", daily, JsonPath.read(daily, "$.rows[6][0]")},
        }) {
            var id = (String) JsonPath.read(report[1], "$.snapshotId");
            var reference = (String) JsonPath.read(report[1], "$.referenceLabel");
            var bytes = mvc.perform(get(BASE + report[0] + "/pdf").param("snapshotId", id).with(user(principal)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (var doc = Loader.loadPDF(bytes)) {
                var pdf = new PDFTextStripper().getText(doc);
                assertTrue(pdf.contains(reference));
                assertTrue(pdf.contains(report[2]));
                assertFalse(pdf.contains("Modelo alterado"));
            }
            var xlsx = mvc.perform(get(BASE + report[0] + "/excel").param("snapshotId", id).with(user(principal)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
                var sheet = book.getSheetAt(0);
                assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains(reference));
                if (report[0].equals("ongoing-trips")) {
                    assertEquals("Van de teste • SVO2201", sheet.getRow(5).getCell(3).getStringCellValue());
                } else {
                    assertEquals(count(report[1], 0), (int) sheet.getRow(5).getCell(1).getNumericCellValue());
                }
            }
            mvc.perform(get(BASE + report[0] + "/excel").param("snapshotId", id)
                    .with(user(principal(UserRole.ADMIN, ALL)))).andExpect(status().isNotFound());
        }
    }

    @Test
    void protectsEachReportAndItsDownloadsUsingSourcePermissions() throws Exception {
        var requestOnly = principal(UserRole.GESTOR_FROTA, Set.of(Permission.TRIP_REQUESTS_READ));
        mvc.perform(get(BASE + "daily-requests").with(user(requestOnly))).andExpect(status().isOk());
        for (String path : List.of("triage", "ongoing-trips")) {
            for (String suffix : List.of("", "/pdf", "/excel")) {
                mvc.perform(get(BASE + path + suffix).param("snapshotId", UUID.randomUUID().toString())
                        .with(user(requestOnly))).andExpect(status().isForbidden());
                mvc.perform(get(BASE + path + suffix).param("snapshotId", UUID.randomUUID().toString())
                        .with(user(principal(UserRole.MOTORISTA, ALL)))).andExpect(status().isForbidden());
            }
        }
        mvc.perform(get(BASE + "daily-requests/excel").param("snapshotId", UUID.randomUUID().toString())
                .with(user(principal(UserRole.ADMIN, Set.of())))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "ongoing-trips").param("period", "invalid")
                .with(user(principal(UserRole.ADMIN, ALL)))).andExpect(status().isBadRequest());
    }

    private String query(String kind, AuthenticatedUser principal) throws Exception {
        return mvc.perform(get(BASE + kind).with(user(principal))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int count(String json, int row) {
        return Integer.parseInt(JsonPath.read(json, "$.rows[" + row + "][1]"));
    }

    private AuthenticatedUser principal(UserRole role, Set<Permission> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "Teste", "svo2@example.test",
                "hash", role, true, permissions);
    }
}
