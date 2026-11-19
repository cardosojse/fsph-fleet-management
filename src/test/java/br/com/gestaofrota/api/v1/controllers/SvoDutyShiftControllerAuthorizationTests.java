package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.responses.SvoCoverageReportResponse;
import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.SvoDutyShiftService;
import br.com.gestaofrota.domain.models.SvoDutyShift;
import br.com.gestaofrota.domain.models.SvoTeam;
import br.com.gestaofrota.infra.security.SecurityConfig;
import br.com.gestaofrota.infra.security.jwt.JwtService;
import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SvoDutyShiftController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SvoDutyShiftControllerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SvoDutyShiftService svoDutyShiftService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Should require authentication for SVO shift endpoints")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/svo/shifts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/svo/shifts/current"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/svo/shifts/coverage-gaps")
                        .param("from", Instant.now().toString())
                        .param("to", Instant.now().plusSeconds(3600).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should enforce SVO_SHIFTS_READ and SVO_SHIFTS_WRITE permissions")
    void enforcesSvoShiftPermissions() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID shiftId = UUID.randomUUID();
        SvoTeam team = new SvoTeam("Equipe Gama", "Desc");
        ReflectionTestUtils.setField(team, "id", teamId);

        Instant start = Instant.parse("2026-06-01T08:00:00Z");
        Instant end = Instant.parse("2026-06-01T20:00:00Z");
        SvoDutyShift shift = new SvoDutyShift(team, null, start, end, "DIURNO", null);
        ReflectionTestUtils.setField(shift, "id", shiftId);

        when(svoDutyShiftService.list(any(), any(), any(), any())).thenReturn(List.of(shift));
        when(svoDutyShiftService.getCurrentShifts(any())).thenReturn(List.of(shift));
        when(svoDutyShiftService.findCoverageGaps(any(), any())).thenReturn(
                new SvoCoverageReportResponse(start, end, true, 0, 0, 0.0, List.of())
        );

        // ATENDENTE_SVO with SVO_SHIFTS_READ can view current shifts
        mockMvc.perform(get("/api/v1/svo/shifts/current")
                        .with(user("atendente").authorities(() -> "SVO_SHIFTS_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").value("Equipe Gama"));

        // GESTOR with SVO_SHIFTS_READ can query coverage gaps
        mockMvc.perform(get("/api/v1/svo/shifts/coverage-gaps")
                        .param("from", start.toString())
                        .param("to", end.toString())
                        .with(user("gestor").authorities(() -> "SVO_SHIFTS_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullyCovered").value(true));

        // ATENDENTE without SVO_SHIFTS_WRITE cannot cancel shift
        mockMvc.perform(patch("/api/v1/svo/shifts/" + shiftId + "/cancel")
                        .with(user("atendente").authorities(() -> "SVO_SHIFTS_READ")))
                .andExpect(status().isForbidden());

        // GESTOR with SVO_SHIFTS_WRITE can cancel shift
        when(svoDutyShiftService.cancel(any(), any())).thenReturn(shift);
        mockMvc.perform(patch("/api/v1/svo/shifts/" + shiftId + "/cancel")
                        .with(user("gestor").authorities(() -> "SVO_SHIFTS_WRITE")))
                .andExpect(status().isOk());
    }
}
