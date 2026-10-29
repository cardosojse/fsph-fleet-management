package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.responses.VehicleMaintenanceSummaryResponse;
import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.MaintenanceService;
import br.com.gestaofrota.application.services.VehicleDocumentService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.infra.security.SecurityConfig;
import br.com.gestaofrota.infra.security.jwt.JwtService;
import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MaintenanceController.class, VehicleController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class MaintenanceControllerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MaintenanceService maintenances;

    @MockitoBean
    private VehicleService vehicles;

    @MockitoBean
    private VehicleDocumentService documents;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void enforcesAuthenticationAndMaintenancePermissions() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        when(maintenances.list(null, null, null)).thenReturn(List.of());
        when(maintenances.listActiveAlerts()).thenReturn(List.of());
        when(vehicles.listAvailable()).thenReturn(List.of());
        when(maintenances.summarizeByVehicle(vehicleId)).thenReturn(new VehicleMaintenanceSummaryResponse(
                vehicleId,
                "ABC1234",
                "Van",
                12_000L,
                false,
                0,
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                List.of()
        ));

        mockMvc.perform(get("/api/v1/maintenances"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/maintenances")
                        .with(user("motorista").authorities(() -> "VEHICLES_READ")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/maintenances")
                        .with(user("gestor").authorities(() -> "MAINTENANCES_READ")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/maintenance-schedules/alerts")
                        .with(user("gestor").authorities(() -> "MAINTENANCES_READ")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/maintenances/summary", vehicleId)
                        .with(user("gestor").authorities(() -> "MAINTENANCES_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehiclePlate").value("ABC1234"));

        mockMvc.perform(get("/api/v1/vehicles/available")
                        .with(user("motorista").authorities(() -> "VEHICLES_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void deniesWriteOperationsWithoutMaintenancesWritePermissionAndValidatesPayload() throws Exception {
        UUID maintenanceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/maintenances")
                        .with(user("gestorLeitura").authorities(() -> "MAINTENANCES_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vehicleId": "%s",
                                  "type": "PREVENTIVE",
                                  "performedOn": "2026-10-28",
                                  "odometerKm": 10000,
                                  "description": "Troca de óleo",
                                  "workshop": "Oficina Central",
                                  "cost": 350.00
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/maintenances/{id}/complete", maintenanceId)
                        .with(user("gestorLeitura").authorities(() -> "MAINTENANCES_READ")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/maintenances")
                        .with(user("gestor").authorities(() -> "MAINTENANCES_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/maintenance-schedules")
                        .with(user("gestor").authorities(() -> "MAINTENANCES_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

