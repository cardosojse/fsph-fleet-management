package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.Route;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RouteResponseTests {
    @Test
    void exposesAssignedUserSeparatelyFromDriverRegistryId() {
        Route route = mock(Route.class, RETURNS_DEEP_STUBS);
        UUID driverId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(route.getDriver().getId()).thenReturn(driverId);
        when(route.getDriver().getUser().getId()).thenReturn(userId);

        RouteResponse response = RouteResponse.from(route);

        assertThat(response.driverId()).isEqualTo(driverId);
        assertThat(response.driverUserId()).isEqualTo(userId).isNotEqualTo(driverId);
    }
}
