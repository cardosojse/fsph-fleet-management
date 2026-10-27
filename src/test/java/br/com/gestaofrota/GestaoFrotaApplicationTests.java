package br.com.gestaofrota;

import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import br.com.gestaofrota.infra.config.startup.AdminInitializer;
import br.com.gestaofrota.infra.config.startup.AdminProperties;
import br.com.gestaofrota.application.services.SectorService;
import br.com.gestaofrota.application.services.UserService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.application.services.VehicleDocumentService;
import br.com.gestaofrota.application.services.DriverService;
import br.com.gestaofrota.application.services.TripService;
import br.com.gestaofrota.application.services.DriverLicenseService;
import br.com.gestaofrota.infra.repositories.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GestaoFrotaApplicationTests {

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
	private TripService tripService;

	@MockitoBean
	private RolePermissionRepository rolePermissionRepository;

	@Autowired
	private AdminProperties adminProperties;

	@Test
	void contextLoadsAndBindsAdminProperties() {
		assertThat(adminProperties.name()).isEqualTo("Administrador de Teste");
		assertThat(adminProperties.email()).isEqualTo("admin.test@exemplo.com");
		assertThat(adminProperties.password()).isEqualTo("senha-de-teste");
		assertThat(adminProperties.sectorName()).isEqualTo("Setor de Teste");
	}

}
