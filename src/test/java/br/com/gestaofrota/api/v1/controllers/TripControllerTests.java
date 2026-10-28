package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.errors.ApiExceptionHandler;
import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.TripService;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.AssignmentProviderUnavailableException;
import br.com.gestaofrota.infra.security.SecurityConfig;
import br.com.gestaofrota.infra.security.jwt.JwtService;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TripController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, ApiExceptionHandler.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TripControllerTests {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TripService trips;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private RevokedTokenService revokedTokenService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void startUsesAuthenticatedUserAndFailsClosedWhenAssignmentProviderIsMissing() throws Exception {
        AuthenticatedUser principal = principal();
        UUID authenticatedUserId = principal.id();
        when(trips.start(eq(authenticatedUserId), any(), anyLong()))
                .thenThrow(new AssignmentProviderUnavailableException());

        mockMvc.perform(post("/api/v1/trips").with(authentication(tokenFor(principal)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + UUID.randomUUID() + "\",\"initialOdometerKm\":12,\"driverId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_PROVIDER_UNAVAILABLE"));

        verify(trips).start(eq(authenticatedUserId), any(), eq(12L));
    }

    @Test
    void startValidatesBodyAndRequiresTripWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/trips").with(authentication(tokenFor(userWith(Permission.VEHICLES_ASSIGNED_READ))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + UUID.randomUUID() + "\",\"initialOdometerKm\":12}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/trips").with(authentication(tokenFor(principal())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleId\":null,\"initialOdometerKm\":-1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(trips);
    }

    @Test
    void completionRequiresAuthenticationAndTripWritePermission() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/trips/" + id + "/completion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalOdometerKm\":15}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/trips/" + id + "/completion")
                        .with(authentication(tokenFor(userWith(Permission.TRIPS_READ))))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalOdometerKm\":15}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(trips);
    }

    @Test
    void completionUsesAuthenticatedUserAndFailsClosedWithoutAssignmentProvider() throws Exception {
        AuthenticatedUser principal = principal();
        UUID tripId = UUID.randomUUID();
        when(trips.complete(eq(principal.id()), eq(tripId), eq(15L)))
                .thenThrow(new AssignmentProviderUnavailableException());

        mockMvc.perform(patch("/api/v1/trips/" + tripId + "/completion")
                        .with(authentication(tokenFor(principal)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalOdometerKm\":15}"))
                .andExpect(status().isServiceUnavailable());
        verify(trips).complete(principal.id(), tripId, 15L);

        mockMvc.perform(patch("/api/v1/trips/" + tripId + "/completion")
                        .with(authentication(tokenFor(principal)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalOdometerKm\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyRequiresManagerPermission() throws Exception {
        mockMvc.perform(get("/api/v1/trips").with(authentication(tokenFor(userWith(Permission.TRIPS_WRITE)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/trips").with(authentication(tokenFor(userWith(Permission.TRIPS_READ)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/trips?vehicleId=invalid")
                        .with(authentication(tokenFor(userWith(UserRole.GESTOR_FROTA, Permission.TRIPS_READ)))))
                .andExpect(status().isBadRequest());
        when(trips.history(isNull(), isNull(), eq(java.time.Instant.parse("2026-01-01T00:00:00Z")),
                eq(java.time.Instant.parse("2026-02-01T00:00:00Z")), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(1, 10)));
        mockMvc.perform(get("/api/v1/trips?startedFrom=2026-01-01T00:00:00Z&startedTo=2026-02-01T00:00:00Z&page=1&size=10")
                        .with(authentication(tokenFor(userWith(UserRole.GESTOR_FROTA, Permission.TRIPS_READ)))))
                .andExpect(status().isOk());
        verify(trips).history(isNull(), isNull(), eq(java.time.Instant.parse("2026-01-01T00:00:00Z")),
                eq(java.time.Instant.parse("2026-02-01T00:00:00Z")),
                eq(PageRequest.of(1, 10, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")))));

        mockMvc.perform(get("/api/v1/trips?page=0&size=101")
                        .with(authentication(tokenFor(userWith(UserRole.GESTOR_FROTA, Permission.TRIPS_READ)))))
                .andExpect(status().isBadRequest());
    }

    private AuthenticatedUser principal() {
        return userWith(Permission.TRIPS_WRITE);
    }

    private AuthenticatedUser userWith(Permission permission) {
        return userWith(UserRole.MOTORISTA, permission);
    }

    private AuthenticatedUser userWith(UserRole role, Permission permission) {
        return new AuthenticatedUser(UUID.randomUUID(), null, role.name(), "m@example.test", "",
                role, true, Set.of(permission));
    }

    private UsernamePasswordAuthenticationToken auth(AuthenticatedUser user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken tokenFor(AuthenticatedUser principal) {
        return auth(principal);
    }
}
