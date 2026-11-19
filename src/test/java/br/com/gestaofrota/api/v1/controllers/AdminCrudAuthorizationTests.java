package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.SectorService;
import br.com.gestaofrota.application.services.UserService;
import br.com.gestaofrota.infra.security.SecurityConfig;
import br.com.gestaofrota.infra.security.jwt.JwtService;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SectorController.class, UserController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminCrudAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectorService sectorService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void allowsAdminToAccessSectorAndUserEndpoints() throws Exception {
        when(sectorService.list()).thenReturn(List.of());
        when(userService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sectors").with(user("admin").authorities(() -> "SECTORS_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").with(user("admin").authorities(() -> "USERS_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void deniesNonAdminAccess() throws Exception {
        when(sectorService.list()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/sectors").with(user("gestor").authorities(() -> "SECTORS_READ")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").with(user("gestor").roles("GESTOR_FROTA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/sectors"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsDomainExceptionFieldsForMissingSector() throws Exception {
        UUID id = UUID.randomUUID();
        when(sectorService.find(id)).thenThrow(new ResourceNotFoundException("Setor", id));

        mockMvc.perform(get("/api/v1/sectors/{id}", id).with(user("admin").authorities(() -> "SECTORS_READ")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("Setor com identificador '" + id + "' não encontrado."))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void allowsUserWithUsersWriteToReactivateUser() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{id}/reactivate", id)
                        .with(user("admin").authorities(() -> "USERS_WRITE")))
                .andExpect(status().isNoContent());

        verify(userService).reactivate(id);
    }

    @Test
    void deniesReactivateWithoutUsersWritePermission() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{id}/reactivate", id)
                        .with(user("gestor").authorities(() -> "USERS_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundWhenReactivatingMissingUser() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Usuário", id)).when(userService).reactivate(id);

        mockMvc.perform(patch("/api/v1/users/{id}/reactivate", id)
                        .with(user("admin").authorities(() -> "USERS_WRITE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsRoleAndSectorChangesWithoutUsersWriteEvenForOwnAccount() throws Exception {
        String body = """
                {"sectorId":"%s","name":"Pessoa","email":"pessoa@exemplo.com",
                 "role":"ADMIN","active":true}
                """.formatted(UUID.randomUUID());
        mockMvc.perform(put("/api/v1/users/{id}", UUID.randomUUID())
                        .with(user("pessoa").authorities(() -> "SECTORS_READ"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users")
                        .with(user("pessoa").authorities(() -> "SECTORS_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"active\":true", "\"password\":\"senha-segura\"")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }
}
