package br.com.gestaofrota.infra.config;

import br.com.gestaofrota.application.services.SectorService;
import br.com.gestaofrota.application.services.UserService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.application.services.VehicleDocumentService;
import br.com.gestaofrota.application.services.DriverService;
import br.com.gestaofrota.application.services.DriverLicenseService;
import br.com.gestaofrota.application.services.MaintenanceService;
import br.com.gestaofrota.application.services.PrimeFuelStationService;
import br.com.gestaofrota.application.services.FuelingService;
import br.com.gestaofrota.infra.repositories.RolePermissionRepository;
import br.com.gestaofrota.infra.config.startup.AdminInitializer;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInitializer adminInitializer;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private SectorService sectorService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private VehicleService vehicleService;

    @MockitoBean
    private VehicleDocumentService vehicleDocumentService;

    @MockitoBean
    private DriverService driverService;

    @MockitoBean
    private DriverLicenseService driverLicenseService;

    @MockitoBean
    private MaintenanceService maintenanceService;
    private PrimeFuelStationService primeFuelStationService;

    @MockitoBean
    private FuelingService fuelingService;

    @MockitoBean
    private RolePermissionRepository rolePermissionRepository;

    @Test
    void documentsBearerAuthenticationGloballyAndKeepsLoginPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty());
    }
}
