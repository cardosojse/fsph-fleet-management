package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.SvoDeathRequestService;
import br.com.gestaofrota.domain.enums.DeathLocationType;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.SvoDeathRequest;
import br.com.gestaofrota.domain.models.User;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SvoDeathRequestController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SvoDeathRequestControllerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SvoDeathRequestService requestService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Deve rejeitar requisição anônima com 401")
    void rejectAnonymousAccessWith401() throws Exception {
        mockMvc.perform(get("/api/v1/svo/requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve rejeitar listagem geral com 403 se usuário não tiver SVO_REQUESTS_READ")
    void rejectListAllWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/svo/requests")
                        .with(user("usuario").authorities(() -> "ROLE_EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve permitir listagem geral se usuário possuir SVO_REQUESTS_READ")
    void allowListAllWithPermission() throws Exception {
        when(requestService.listAll(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/svo/requests")
                        .with(user("atendente").authorities(() -> "SVO_REQUESTS_READ")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve permitir que usuário comum com SVO_REQUESTS_WRITE crie solicitação de atendimento SVO")
    void allowCreateWithSvoRequestsWrite() throws Exception {
        Sector sector = new Sector("Setor Externo");
        User requester = new User(sector, "Solicitante", "solicitante@fsph.se.gov.br", "hash", UserRole.EMPLOYEE);
        ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());

        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requester,
                "Nome Falecido",
                75,
                "MALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua X, 100",
                "Centro",
                "Aracaju",
                null,
                "Maria",
                "79999999999",
                "FILHA",
                null,
                false,
                null
        );
        ReflectionTestUtils.setField(deathRequest, "id", UUID.randomUUID());

        when(requestService.create(any(), any())).thenReturn(deathRequest);

        String body = """
                {
                    "deceasedName": "Nome Falecido",
                    "deceasedAge": 75,
                    "deceasedGender": "MALE",
                    "occurredAt": "2026-11-18T12:00:00Z",
                    "deathLocationType": "DOMICILE",
                    "address": "Rua X, 100",
                    "neighborhood": "Centro",
                    "city": "Aracaju",
                    "contactName": "Maria",
                    "contactPhone": "79999999999",
                    "relationshipWithDeceased": "FILHA"
                }
                """;

        mockMvc.perform(post("/api/v1/svo/requests")
                        .with(user("solicitante").authorities(() -> "SVO_REQUESTS_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Deve permitir que solicitante consulte suas próprias solicitações via /my-requests")
    void allowListMyRequests() throws Exception {
        when(requestService.listMyRequests(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/svo/requests/my-requests")
                        .with(user("solicitante").authorities(() -> "SVO_REQUESTS_WRITE")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve rejeitar triagem com 403 se usuário não tiver SVO_REQUESTS_MANAGE")
    void rejectTriageWithoutPermission() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                {
                    "approved": true
                }
                """;

        mockMvc.perform(patch("/api/v1/svo/requests/" + id + "/triage")
                        .with(user("solicitante").authorities(() -> "SVO_REQUESTS_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve permitir triagem se usuário tiver SVO_REQUESTS_MANAGE")
    void allowTriageWithPermission() throws Exception {
        UUID id = UUID.randomUUID();
        Sector sector = new Sector("SVO");
        User requester = new User(sector, "Solicitante", "solicitante@fsph.se.gov.br", "hash", UserRole.EMPLOYEE);
        ReflectionTestUtils.setField(requester, "id", UUID.randomUUID());

        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requester,
                "Nome Falecido",
                75,
                "MALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua X, 100",
                "Centro",
                "Aracaju",
                null,
                "Maria",
                "79999999999",
                "FILHA",
                null,
                false,
                null
        );
        ReflectionTestUtils.setField(deathRequest, "id", id);

        when(requestService.triage(eq(id), any(), any())).thenReturn(deathRequest);

        String body = """
                {
                    "approved": true
                }
                """;

        mockMvc.perform(patch("/api/v1/svo/requests/" + id + "/triage")
                        .with(user("atendente").authorities(() -> "SVO_REQUESTS_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
