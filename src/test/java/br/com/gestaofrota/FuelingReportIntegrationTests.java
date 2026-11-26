package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.models.*;
import br.com.gestaofrota.infra.repositories.*;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.reports.limits.max-rows=4")
@AutoConfigureMockMvc
@Transactional
class FuelingReportIntegrationTests extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SectorRepository sectors;
    @Autowired VehicleRepository vehicles;
    @Autowired UserRepository users;
    @Autowired PrimeFuelStationRepository stations;
    @Autowired FuelingRepository fuelings;

    private AuthenticatedUser principal(UserRole role, UUID sector, boolean read) {
        return new AuthenticatedUser(UUID.randomUUID(), sector, "Teste", "teste@example.com", "hash",
                role, true, read ? Set.of(Permission.FUELINGS_READ) : Set.of());
    }

    @Test
    void queriesPersistedRowsAndProtectsEveryRepresentation() throws Exception {
        var own = sectors.save(new Sector("Relatório " + UUID.randomUUID()));
        var other = sectors.save(new Sector("Outro " + UUID.randomUUID()));
        var author = users.save(new User(other, "Autor", UUID.randomUUID() + "@example.com", "hash", UserRole.MOTORISTA));
        var station = stations.save(new PrimeFuelStation("Posto", "12345678000190", "Aracaju", "SE", "Rua A"));
        var a = vehicles.save(new Vehicle(own, "RPT0001", "Modelo próprio", 4, "Teste", 100));
        var b = vehicles.save(new Vehicle(other, "RPT0002", "Modelo externo", 4, "Teste", 100));
        var unassigned = vehicles.save(new Vehicle(null, "RPT0003", "Sem setor", 4, "Teste", 100));
        var day = LocalDate.of(2026, 1, 15);
        for (var v : List.of(a, b, unassigned)) {
            fuelings.saveAndFlush(Fueling.register(v, station, author, day, 100, null,
                    new BigDecimal("3.000"), new BigDecimal("10.00"), FuelType.DIESEL, null));
        }
        var manager = principal(UserRole.GESTOR_FROTA, own.getId(), true);
        var admin = principal(UserRole.ADMIN, null, true);
        fuelings.saveAndFlush(Fueling.register(a, station, author, day.minusDays(1), 99, null,
                BigDecimal.ONE, BigDecimal.ONE, FuelType.DIESEL, null));
        fuelings.saveAndFlush(Fueling.register(a, station, author, day.plusDays(1), 102, null,
                BigDecimal.ONE, BigDecimal.ONE, FuelType.DIESEL, null));
        var page = org.springframework.data.domain.PageRequest.of(0, 4);
        assertEquals(1, fuelings.findForReport(day, day, null, false, own.getId(), page).size());
        assertEquals(3, fuelings.findForReport(day, day, null, true, null, page).size());
        assertEquals(1, fuelings.findForReport(day, day, a.getId(), true, null, page).size());
        assertTrue(fuelings.findForReport(day, day, b.getId(), false, own.getId(), page).isEmpty());
        String base = "/api/v1/reports/fuelings";
        String dates = "?startDate=2026-01-15&endDate=2026-01-15";
        mvc.perform(get(base + dates).with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].vehicleId").value(a.getId().toString()))
                .andExpect(jsonPath("$.items[0].pricePerLiter").value(3.33))
                .andExpect(jsonPath("$.totals.totalValue").value(10.00));
        mvc.perform(get(base + dates).with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3));
        for (String suffix : List.of("", "/pdf", "/excel")) {
            mvc.perform(get(base + suffix + dates)).andExpect(status().isUnauthorized());
            for (var role : UserRole.values()) {
                if (role != UserRole.ADMIN && role != UserRole.GESTOR_FROTA)
                    mvc.perform(get(base + suffix + dates).with(user(principal(role, own.getId(), true))))
                            .andExpect(status().isForbidden());
            }
            mvc.perform(get(base + suffix + dates).with(user(principal(UserRole.ADMIN, own.getId(), false))))
                    .andExpect(status().isForbidden());
            mvc.perform(get(base + suffix + dates).with(user(principal(UserRole.GESTOR_FROTA, null, true))))
                    .andExpect(status().isForbidden());
            for (UUID id : List.of(b.getId(), unassigned.getId(), UUID.randomUUID())) {
                mvc.perform(get(base + suffix + dates + "&vehicleId=" + id).with(user(manager)))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            }
            mvc.perform(get(base + suffix + "?startDate=2026-01-16&endDate=2026-01-15").with(user(manager)))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(base + suffix + dates + "&vehicleId=invalid").with(user(manager)))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(base + suffix).with(user(manager))).andExpect(status().isBadRequest());
            mvc.perform(get(base + suffix + "?startDate=invalid&endDate=2026-01-15").with(user(manager)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(base + "?startDate=2026-01-17&endDate=2026-01-17&vehicleId=" + a.getId()).with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totals.totalFuelings").value(0));
        byte[] pdf = mvc.perform(get(base + "/pdf" + dates).with(user(manager)))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"abastecimentos.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = Loader.loadPDF(pdf)) {
            var text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("RPT0001"));
            assertFalse(text.contains("RPT0002"));
            assertTrue(text.contains("10,00"));
        }
        byte[] excel = mvc.perform(get(base + "/excel" + dates).with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"abastecimentos.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals("RPT0001", workbook.getSheetAt(0).getRow(5).getCell(1).getStringCellValue());
            assertEquals(10.0, workbook.getSheetAt(0).getRow(6).getCell(6).getNumericCellValue());
        }
        // A fourth matching record exceeds the three data rows allowed with a total row.
        fuelings.saveAndFlush(Fueling.register(a, station, author, day, 101, 100L,
                BigDecimal.ONE, BigDecimal.ONE, FuelType.DIESEL, null));
        for (String suffix : List.of("", "/pdf", "/excel")) {
            mvc.perform(get(base + suffix + dates).with(user(admin))).andExpect(status().isBadRequest());
            mvc.perform(get(base + suffix + dates).with(user(manager))).andExpect(status().isOk());
        }
        // Historical rows follow the vehicle's current sector after transfer.
        a.update(other, a.getPlate(), a.getModel(), 4, "Teste", 101);
        vehicles.flush();
        mvc.perform(get(base + dates).with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }
}
