package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTests {
    @Test
    void preservesMissingLegacyAssignmentAsAnAdministrativePendingItem() {
        User user = new User(null, "Legado", "legado@exemplo.com", "hash", UserRole.EMPLOYEE);

        UserResponse response = UserResponse.from(user);

        assertThat(response.sectorId()).isNull();
        assertThat(response.sectorName()).isNull();
        assertThat(response.role()).isEqualTo(UserRole.EMPLOYEE);
    }
}
