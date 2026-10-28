package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.DriverLicenseService;
import br.com.gestaofrota.application.services.DriverService;
import br.com.gestaofrota.application.services.VehicleDocumentService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.application.services.TripUtilizationService;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.AssignmentProviderUnavailableException;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import br.com.gestaofrota.domain.models.Vehicle;
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

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

@WebMvcTest({VehicleController.class, DriverController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class FleetControllerAuthorizationTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private VehicleService vehicles;
    @MockitoBean private VehicleDocumentService documents;
    @MockitoBean private TripUtilizationService tripUtilization;
    @MockitoBean private DriverService drivers;
    @MockitoBean private DriverLicenseService licenses;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private RevokedTokenService revokedTokenService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void requiresAuthenticationAndFleetPermissions() throws Exception {
        when(vehicles.list()).thenReturn(List.of());
        when(drivers.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized());
        AuthenticatedUser motorista = new AuthenticatedUser(UUID.randomUUID(), null, "Motorista", "m@example.test",
                "", UserRole.MOTORISTA, true, Set.of(Permission.VEHICLES_READ));
        mockMvc.perform(get("/api/v1/vehicles").with(authentication(auth(motorista))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/drivers").with(user("motorista").authorities(() -> "VEHICLES_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/drivers").with(user("gestor").authorities(() -> "DRIVERS_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void assignedVehiclesRequiresSpecificPermissionAndOfficialProvider() throws Exception {
        AuthenticatedUser motorista = new AuthenticatedUser(UUID.randomUUID(), null, "Motorista", "m@example.test",
                "", UserRole.MOTORISTA, true, Set.of(Permission.VEHICLES_ASSIGNED_READ));
        AuthenticatedUser gestor = new AuthenticatedUser(UUID.randomUUID(), null, "Gestor", "g@example.test",
                "", UserRole.GESTOR_FROTA, true, Set.of(Permission.VEHICLES_READ));
        when(vehicles.assignedToUser(motorista.id())).thenThrow(new AssignmentProviderUnavailableException());

        mockMvc.perform(get("/api/v1/vehicles/me").with(authentication(auth(motorista))))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/v1/vehicles/me").with(user("motorista").authorities(() -> "VEHICLES_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles").with(authentication(auth(motorista))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles/" + UUID.randomUUID()).with(authentication(auth(motorista))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles").with(authentication(auth(gestor))))
                .andExpect(status().isOk());
    }

    @Test
    void mileageIsAvailableToFleetManagersButNotDriversAndDriverCannotReadVehicleDocuments() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(vehicleId);
        when(vehicle.getCurrentOdometerKm()).thenReturn(12345L);
        when(vehicles.find(vehicleId)).thenReturn(vehicle);
        AuthenticatedUser motoristaWithLegacyRead = new AuthenticatedUser(UUID.randomUUID(), null, "Motorista",
                "m@example.test", "", UserRole.MOTORISTA, true, Set.of(Permission.VEHICLES_READ));
        AuthenticatedUser gestor = new AuthenticatedUser(UUID.randomUUID(), null, "Gestor", "g@example.test",
                "", UserRole.GESTOR_FROTA, true, Set.of(Permission.VEHICLES_READ));

        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/mileage")
                        .with(authentication(auth(motoristaWithLegacyRead))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/documents")
                        .with(authentication(auth(motoristaWithLegacyRead))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/mileage")
                        .with(authentication(auth(gestor))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                        .andExpect(jsonPath("$.currentOdometerKm").value(12345));
    }

    @Test
    void utilizationRequiresManagerPermissionAndDateFilters() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        AuthenticatedUser motorista = new AuthenticatedUser(UUID.randomUUID(), null, "Motorista", "m@example.test",
                "", UserRole.MOTORISTA, true, Set.of(Permission.TRIPS_READ));
        AuthenticatedUser gestor = new AuthenticatedUser(UUID.randomUUID(), null, "Gestor", "g@example.test",
                "", UserRole.GESTOR_FROTA, true, Set.of(Permission.TRIPS_READ));
        when(tripUtilization.calculateRate(vehicleId, java.time.LocalDate.parse("2026-01-01"),
                java.time.LocalDate.parse("2026-01-04"))).thenReturn(new BigDecimal("50.00"));

        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/utilization")
                        .with(authentication(auth(motorista)))
                        .param("startDate", "2026-01-01").param("endDate", "2026-01-04"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/utilization")
                        .with(authentication(auth(gestor))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/vehicles/" + vehicleId + "/utilization")
                        .with(authentication(auth(gestor)))
                        .param("startDate", "2026-01-01").param("endDate", "2026-01-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value(vehicleId.toString()))
                .andExpect(jsonPath("$.utilizationRatePercent").value(50.00))
                .andExpect(jsonPath("$.currentOdometerKm").doesNotExist());
    }

    private UsernamePasswordAuthenticationToken auth(AuthenticatedUser user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void validatesVehicleAndDriverRequests() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .with(user("gestor").authorities(() -> "VEHICLES_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/drivers")
                        .with(user("gestor").authorities(() -> "DRIVERS_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
