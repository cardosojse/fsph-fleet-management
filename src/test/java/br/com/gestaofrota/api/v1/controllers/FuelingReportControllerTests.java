package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.application.reports.*;
import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.models.*;
import br.com.gestaofrota.infra.config.ReportConfiguration;
import br.com.gestaofrota.infra.repositories.FuelingRepository;
import br.com.gestaofrota.infra.reports.*;
import br.com.gestaofrota.infra.security.SecurityConfig;
import br.com.gestaofrota.infra.security.jwt.JwtService;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import br.com.gestaofrota.infra.security.services.*;
import br.com.gestaofrota.api.v1.errors.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FuelingReportController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        FuelingReportQueryService.class, FuelingReportService.class, FuelingReportMapper.class,
        ReportGenerationService.class, ReportValidator.class, ReportConfiguration.class,
        PdfReportExporter.class, ExcelReportExporter.class})
class FuelingReportControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean FuelingRepository repository;
    @MockitoBean JwtService jwtService;
    @MockitoBean RevokedTokenService revokedTokenService;
    @MockitoBean CustomUserDetailsService customUserDetailsService;
    @Autowired ReportLimits limits;
    private final String base = "/api/v1/reports/fuelings";
    private final String dates = "?startDate=2026-01-15&endDate=2026-01-15";
    private AuthenticatedUser principal(UserRole role, UUID sector, boolean read) {
        return new AuthenticatedUser(UUID.randomUUID(), sector, "Teste", "teste@example.com", "hash", role,
                true, read ? Set.of(Permission.FUELINGS_READ) : Set.of());
    }

    @Test
    void enforcesAuthorizationAndDoesNotExposeVehiclesAcrossAllFormats() throws Exception {
        for (String suffix : List.of("", "/pdf", "/excel")) {
            mvc.perform(get(base + suffix + dates)).andExpect(status().isUnauthorized());
            for (var role : UserRole.values()) {
                mvc.perform(get(base + suffix + dates).with(user(principal(role, UUID.randomUUID(), false))))
                        .andExpect(status().isForbidden());
                if (role != UserRole.ADMIN && role != UserRole.GESTOR_FROTA)
                    mvc.perform(get(base + suffix + dates).with(user(principal(role, UUID.randomUUID(), true))))
                            .andExpect(status().isForbidden());
            }
            mvc.perform(get(base + suffix + dates).with(user(principal(UserRole.GESTOR_FROTA, null, true))))
                    .andExpect(status().isForbidden());
            mvc.perform(get(base + suffix + dates + "&vehicleId=" + UUID.randomUUID())
                            .with(user(principal(UserRole.GESTOR_FROTA, UUID.randomUUID(), true))))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }

    @Test
    void validatesFiltersAndReturnsEmptyReports() throws Exception {
        var admin = principal(UserRole.ADMIN, null, true);
        when(repository.findForReport(any(), any(), any(), anyBoolean(), any(), any())).thenReturn(List.of());
        for (String suffix : List.of("", "/pdf", "/excel")) {
            for (String invalid : List.of("", "?startDate=bad&endDate=2026-01-15",
                    "?startDate=2026-01-16&endDate=2026-01-15", dates + "&vehicleId=bad"))
                mvc.perform(get(base + suffix + invalid).with(user(admin))).andExpect(status().isBadRequest());
            mvc.perform(get(base + suffix + dates).with(user(admin))).andExpect(status().isOk());
        }
        mvc.perform(get(base + dates).with(user(admin))).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totals.totalValue").value(0));
    }

    @Test
    void rendersSameMappedDataAndTotalsInJsonPdfAndExcel() throws Exception {
        var sector = new Sector("Frota");
        var vehicle = new Vehicle(sector, "RPT0001", "Modelo", 4, "Teste", 100);
        ReflectionTestUtils.setField(vehicle, "id", UUID.randomUUID());
        var author = new User(sector, "Autor", "autor@example.com", "hash", UserRole.MOTORISTA);
        var station = new PrimeFuelStation("Posto", "12345678000190", "Aracaju", "SE", "Rua A");
        var fueling = Fueling.register(vehicle, station, author, LocalDate.of(2026, 1, 15), 100, null,
                new BigDecimal("3.000"), new BigDecimal("10.00"), FuelType.DIESEL, null);
        ReflectionTestUtils.setField(fueling, "id", UUID.randomUUID());
        when(repository.findForReport(any(), any(), any(), anyBoolean(), any(), any())).thenReturn(List.of(fueling));
        when(repository.existsReportVehicle(eq(vehicle.getId()), anyBoolean(), any())).thenReturn(true);
        var manager = principal(UserRole.GESTOR_FROTA, UUID.randomUUID(), true);
        mvc.perform(get(base + dates).with(user(manager))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pricePerLiter").value(3.33))
                .andExpect(jsonPath("$.totals.totalValue").value(10));
        byte[] pdf = mvc.perform(get(base + "/pdf" + dates).with(user(manager))).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"abastecimentos.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = Loader.loadPDF(pdf)) {
            var text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("RPT0001"));
            assertTrue(text.contains("10,00"));
        }
        byte[] excel = mvc.perform(get(base + "/excel" + dates).with(user(manager))).andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"abastecimentos.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals("RPT0001", workbook.getSheetAt(0).getRow(5).getCell(1).getStringCellValue());
            assertEquals(10.0, workbook.getSheetAt(0).getRow(6).getCell(6).getNumericCellValue());
        }
        verify(repository, times(3)).findForReport(eq(LocalDate.of(2026, 1, 15)), eq(LocalDate.of(2026, 1, 15)),
                isNull(), eq(false), eq(manager.sectorId()), any());
        mvc.perform(get(base + dates + "&vehicleId=" + vehicle.getId()).with(user(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        verify(repository).findForReport(eq(LocalDate.of(2026, 1, 15)), eq(LocalDate.of(2026, 1, 15)),
                eq(vehicle.getId()), eq(false), eq(manager.sectorId()), any());
        when(repository.findForReport(any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(Collections.nCopies(limits.maxRows(), fueling));
        for (String suffix : List.of("", "/pdf", "/excel")) {
            mvc.perform(get(base + suffix + dates).with(user(manager))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }
}
