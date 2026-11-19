package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.errors.RestAccessDeniedHandler;
import br.com.gestaofrota.api.v1.errors.RestAuthenticationEntryPoint;
import br.com.gestaofrota.application.services.FuelCardService;
import br.com.gestaofrota.domain.models.FuelCard;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FuelCardController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class FuelCardControllerAuthorizationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FuelCardService fuelCardService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("Deve rejeitar requisição anônima com 401")
    void rejectAnonymousAccessWith401() throws Exception {
        mockMvc.perform(get("/api/v1/fuel-cards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve rejeitar listagem com 403 se usuário não tiver FUEL_CARDS_READ")
    void rejectListWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/fuel-cards")
                        .with(user("usuario").authorities(() -> "ROLE_EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve permitir listagem se usuário possuir FUEL_CARDS_READ")
    void allowListWithPermission() throws Exception {
        when(fuelCardService.list(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/fuel-cards")
                        .with(user("gestor").authorities(() -> "FUEL_CARDS_READ")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve rejeitar cadastro com 403 se usuário não tiver FUEL_CARDS_WRITE")
    void rejectCreateWithoutPermission() throws Exception {
        String body = """
                {
                    "cardNumber": "6034123456789012",
                    "cardholderName": "Cartão Prime",
                    "expirationDate": "2027-12-31",
                    "totalLimit": 5000.00,
                    "availableBalance": 5000.00
                }
                """;

        mockMvc.perform(post("/api/v1/fuel-cards")
                        .with(user("leitor").authorities(() -> "FUEL_CARDS_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve permitir cadastro com 201 se usuário possuir FUEL_CARDS_WRITE")
    void allowCreateWithPermission() throws Exception {
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Cartão Prime",
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                LocalDate.of(2027, 12, 31),
                null
        );
        ReflectionTestUtils.setField(card, "id", UUID.randomUUID());

        when(fuelCardService.create(any(), any())).thenReturn(card);

        String body = """
                {
                    "cardNumber": "6034123456789012",
                    "cardholderName": "Cartão Prime",
                    "expirationDate": "2027-12-31",
                    "totalLimit": 5000.00,
                    "availableBalance": 5000.00
                }
                """;

        mockMvc.perform(post("/api/v1/fuel-cards")
                        .with(user("gestor").authorities(() -> "FUEL_CARDS_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Deve permitir vincular e desvincular veículo com FUEL_CARDS_WRITE")
    void allowBindAndUnbindVehicleWithPermission() throws Exception {
        UUID cardId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Cartão Prime",
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                LocalDate.of(2027, 12, 31),
                null
        );
        ReflectionTestUtils.setField(card, "id", cardId);

        when(fuelCardService.bindVehicle(eq(cardId), eq(vehicleId), any())).thenReturn(card);
        when(fuelCardService.unbindVehicle(eq(cardId), any())).thenReturn(card);

        String bindBody = """
                {
                    "vehicleId": "%s"
                }
                """.formatted(vehicleId);

        mockMvc.perform(post("/api/v1/fuel-cards/" + cardId + "/bind-vehicle")
                        .with(user("gestor").authorities(() -> "FUEL_CARDS_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBody))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/fuel-cards/" + cardId + "/bind-vehicle")
                        .with(user("gestor").authorities(() -> "FUEL_CARDS_WRITE")))
                .andExpect(status().isOk());
    }
}
