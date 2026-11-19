package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.SvoTeamService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SvoTeamController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SvoTeamControllerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SvoTeamService svoTeamService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Should require authentication for SVO team endpoints")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/svo/teams"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should enforce SVO_TEAMS_READ and SVO_TEAMS_WRITE permissions")
    void enforcesSvoTeamPermissions() throws Exception {
        UUID teamId = UUID.randomUUID();
        SvoTeam team = new SvoTeam("Equipe Teste", "Desc");
        ReflectionTestUtils.setField(team, "id", teamId);

        when(svoTeamService.list(any())).thenReturn(List.of(team));
        when(svoTeamService.find(teamId)).thenReturn(team);

        // Deny user without authority
        mockMvc.perform(get("/api/v1/svo/teams")
                        .with(user("user").authorities(() -> "SOME_OTHER_AUTH")))
                .andExpect(status().isForbidden());

        // Allow user with SVO_TEAMS_READ
        mockMvc.perform(get("/api/v1/svo/teams")
                        .with(user("gestor").authorities(() -> "SVO_TEAMS_READ")))
                .andExpect(status().isOk());

        // Deny POST without SVO_TEAMS_WRITE
        mockMvc.perform(post("/api/v1/svo/teams")
                        .with(user("atendente").authorities(() -> "SVO_TEAMS_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Equipe Beta\",\"description\":\"Desc\"}"))
                .andExpect(status().isForbidden());

        // Allow POST with SVO_TEAMS_WRITE
        when(svoTeamService.create(any(), any(), any(), any())).thenReturn(team);
        mockMvc.perform(post("/api/v1/svo/teams")
                        .with(user("gestor").authorities(() -> "SVO_TEAMS_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Equipe Beta\",\"description\":\"Desc\"}"))
                .andExpect(status().isCreated());
    }
}
