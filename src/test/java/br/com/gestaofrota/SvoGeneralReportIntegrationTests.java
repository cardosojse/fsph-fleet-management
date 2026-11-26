package br.com.gestaofrota;

import br.com.gestaofrota.application.reports.ReportGenerationService;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
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
class SvoGeneralReportIntegrationTests extends PostgresIntegrationTest {
    private static final String BASE = "/api/v1/reports/svo/";
    private static final List<String> ORDER = List.of("availability", "triage", "daily-requests", "fleet-status",
            "ongoing-trips", "vehicle-consumption", "recent-fuelings", "maintenance-alerts");
    private static final Set<Permission> ALL = Set.of(Permission.VEHICLES_READ, Permission.TRIPS_READ,
            Permission.TRIP_REQUESTS_READ, Permission.DRIVERS_READ, Permission.SECTORS_READ,
            Permission.FUELINGS_READ, Permission.MAINTENANCES_READ);

    @Autowired MockMvc mvc;
    @Autowired ReportGenerationService generator;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void consolidatesApprovedPanelsAndExportsOneImmutableResult() throws Exception {
        var actor = principal(ALL);
        var sector = db.queryForObject("INSERT INTO sectors (name) VALUES ('SVO etapa 4') RETURNING id", UUID.class);
        var emptySector = db.queryForObject("INSERT INTO sectors (name) VALUES ('Sem viagens etapa 4') RETURNING id", UUID.class);
        db.update("""
                INSERT INTO vehicles (sector_id, plate, model, capacity, usage_type, current_odometer_km)
                VALUES (?, 'SVO4401', 'Van consolidada', 4, 'Teste', 100)
                """, sector);
        var admin = db.queryForObject("SELECT id FROM users WHERE email = 'admin.test@exemplo.com'", UUID.class);
        db.update("INSERT INTO drivers (user_id) VALUES (?) ON CONFLICT DO NOTHING", admin);
        var driver = db.queryForObject("SELECT id FROM drivers WHERE user_id = ?", UUID.class, admin);
        db.update("""
                INSERT INTO trips (vehicle_id, driver_id, started_at, initial_odometer_km, status)
                SELECT id, ?, now() - interval '9 days', 100, 'OPEN' FROM vehicles WHERE plate = 'SVO4401'
                """, driver);

        String all = read("general?period=all&sectorId=" + sector, actor);
        String filtered = read("general?period=week&sectorId=" + emptySector, actor);
        String defaultToday = read("general?sectorId=" + sector, actor);
        assertEquals(ORDER, JsonPath.read(all, "$.sections[*].indicator"));
        assertTrue(rows(defaultToday, 4).isEmpty());
        assertTrue(rows(filtered, 4).isEmpty());
        assertEquals(1, rows(all, 4).size());
        assertTrue(rows(all, 4).getFirst().get(3).contains("SVO4401"));

        for (int i = 0; i < ORDER.size(); i++) {
            if (i != 4) assertEquals(rows(all, i), rows(filtered, i));
            if (i == 0 || i == 3) continue;
            var path = ORDER.get(i) + (i == 4 ? "?period=all&sectorId=" + sector : "");
            assertEquals(rows(all, i), JsonPath.read(read(path, actor), "$.rows"));
        }
        assertEquals(rows(all, 0), rows(all, 3));
        assertEquals(7, rows(all, 2).size());
        assertTrue(rows(all, 5).size() <= 5);
        assertTrue(rows(all, 6).size() <= 5);
        assertTrue(Integer.parseInt(rows(all, 1).getLast().get(1)) >= 1);

        db.update("UPDATE trips SET status = 'COMPLETED', ended_at = now(), final_odometer_km = 110"
                + " WHERE vehicle_id = (SELECT id FROM vehicles WHERE plate = 'SVO4401')");
        entityManager.clear();
        assertTrue(rows(read("general?period=all&sectorId=" + sector, actor), 4).isEmpty());
        String snapshot = JsonPath.read(all, "$.snapshotId");
        String reference = JsonPath.read(all, "$.referenceLabel");
        List<String> titles = JsonPath.read(all, "$.sections[*].title");
        try (var doc = Loader.loadPDF(export("pdf", snapshot, actor))) {
            assertTrue(doc.getNumberOfPages() < titles.size(), "Pequenos painéis compartilham uma página");
            var text = new PDFTextStripper().getText(doc);
            int previous = -1;
            for (String title : titles) {
                int position = text.indexOf(title);
                assertTrue(position > previous, title);
                previous = position;
            }
            assertEquals(1, text.split("Tipo: Geral", -1).length - 1);
            assertTrue(text.contains(reference));
            assertTrue(text.contains("SVO4401"));
            assertTrue(text.contains("Página " + doc.getNumberOfPages()));
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(export("excel", snapshot, actor)))) {
            assertEquals(1, book.getNumberOfSheets());
            var sheet = book.getSheet("Geral SVO");
            assertNotNull(sheet);
            assertNotNull(book.getPrintArea(0));
            assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().contains(reference));
            int index = 4;
            for (int i = 0; i < titles.size(); i++) {
                assertEquals(titles.get(i), sheet.getRow(index).getCell(0).getStringCellValue());
                List<List<String>> panel = rows(all, i);
                int first = index + 3;
                if (i == 0 || i == 3) {
                    assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC,
                            sheet.getRow(first).getCell(1).getCellType());
                    assertEquals("0.00%", sheet.getRow(first).getCell(2).getCellStyle().getDataFormatString());
                }
                if (i == 4) assertTrue(sheet.getRow(first).getCell(3).getStringCellValue().contains("SVO4401"));
                if (i == 6 && !panel.isEmpty()) {
                    assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC,
                            sheet.getRow(first).getCell(2).getCellType());
                    assertTrue(sheet.getRow(first).getCell(3).getCellStyle().getDataFormatString().contains("R$"));
                }
                index = first + Math.max(1, panel.size()) + 1;
            }
        }
        mvc.perform(get(BASE + "general/pdf").param("snapshotId", snapshot).with(user(principal(ALL))))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void rejectsIncompletePermissionsAndPreservesUnicodeOutsidePdf() throws Exception {
        for (Permission missing : ALL) {
            var partial = EnumSet.copyOf(ALL);
            partial.remove(missing);
            for (String suffix : List.of("", "/pdf", "/excel")) {
                mvc.perform(get(BASE + "general" + suffix).param("snapshotId", UUID.randomUUID().toString())
                        .with(user(principal(partial)))).andExpect(status().isForbidden());
            }
        }
        var actor = principal(ALL);
        db.update("""
                INSERT INTO vehicles (plate, model, capacity, usage_type, current_odometer_km)
                VALUES ('SVO4402', 'Teste', 4, 'Teste', 100)
                """);
        db.update("""
                INSERT INTO preventive_maintenance_schedules (vehicle_id, service_name, notes, target_date)
                SELECT id, 'Teste Unicode', ?, CURRENT_DATE - 1 FROM vehicles WHERE plate = 'SVO4402'
                """, "Observação 漢");
        var view = read("general", actor);
        String id = JsonPath.read(view, "$.snapshotId");
        assertTrue(rows(view, 7).stream().anyMatch(row -> row.get(1).contains("Observação 漢")));
        mvc.perform(get(BASE + "general/pdf").param("snapshotId", id).with(user(actor)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("Alertas de manutenção")));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(export("excel", id, actor)))) {
            var sheet = book.getSheet("Geral SVO");
            assertTrue(java.util.stream.StreamSupport.stream(sheet.spliterator(), false).anyMatch(row ->
                    row.getCell(1) != null
                            && row.getCell(1).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                            && row.getCell(1).getStringCellValue().contains("Observação 漢")));
        }
    }

    @Test
    void pdfKeepsPanelWithItsTableAndRepeatsHeadingsAcrossPages() throws Exception {
        var small = new ReportData("Painel inicial", "Primeira seção", List.of("Valor"),
                List.of(List.of("Registro inicial")));
        var longTable = new ReportData("Painel longo", "Tabela paginada", List.of("Registro", "Detalhes"),
                java.util.stream.IntStream.rangeClosed(1, 65)
                        .mapToObj(i -> List.of("LINHA-%03d".formatted(i), "Texto do registro")).toList());
        var last = new ReportData("Painel final", "Fim do relatório", List.of("Informado"),
                List.of(List.of("Registro final")));
        try (var pdf = Loader.loadPDF(generator.generateSections("Relatório geral", List.of(small, longTable, last),
                ReportFormat.PDF))) {
            var stripper = new PDFTextStripper();
            int longTablePages = 0;
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                var text = stripper.getText(pdf);
                if (text.contains("Painel longo")) {
                    assertTrue(text.contains("LINHA-001"));
                    assertTrue(text.contains("Painel inicial"));
                }
                if (text.contains("LINHA-")) {
                    longTablePages++;
                    assertTrue(text.contains("Detalhes"));
                }
                if (text.contains("Painel final")) assertTrue(text.contains("Registro final"));
            }
            assertTrue(longTablePages >= 2);
            stripper.setStartPage(1);
            stripper.setEndPage(pdf.getNumberOfPages());
            var text = stripper.getText(pdf);
            for (int i = 1; i <= 65; i++) {
                var marker = "LINHA-%03d".formatted(i);
                assertEquals(1, text.split(marker, -1).length - 1, marker);
            }
        }
    }

    private static List<List<String>> rows(String view, int index) {
        return JsonPath.read(view, "$.sections[" + index + "].rows");
    }

    private String read(String path, AuthenticatedUser actor) throws Exception {
        return mvc.perform(get(BASE + path).with(user(actor))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private byte[] export(String format, String snapshot, AuthenticatedUser actor) throws Exception {
        return mvc.perform(get(BASE + "general/" + format).param("snapshotId", snapshot).with(user(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private AuthenticatedUser principal(Set<Permission> permissions) {
        return new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "Teste", "svo4@example.test", "hash",
                UserRole.GESTOR_FROTA, true, permissions);
    }
}
