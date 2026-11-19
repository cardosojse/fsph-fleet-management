package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createsUserWithNormalizedEmailAndEncodedPassword() {
        UUID sectorId = UUID.randomUUID();
        Sector sector = new Sector("Financeiro");
        when(sectorRepository.findById(sectorId)).thenReturn(Optional.of(sector));
        when(passwordEncoder.encode("senha-segura")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.create(
                sectorId,
                " Maria ",
                " MARIA@EXEMPLO.COM ",
                "senha-segura",
                UserRole.GESTOR_FROTA
        );

        assertEquals("Maria", user.getName());
        assertEquals("maria@exemplo.com", user.getEmail());
        assertEquals("hash", user.getPassword());
    }

    @Test
    void doesNotDeactivateLastActiveAdmin() {
        UUID id = UUID.randomUUID();
        User admin = new User(new Sector("Administração"), "Admin", "admin@exemplo.com", "hash", UserRole.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(admin));
        when(userRepository.lockActiveByRole(UserRole.ADMIN)).thenReturn(List.of(admin));

        assertThrows(ConflictException.class, () -> userService.deactivate(id));
        assertTrue(admin.isActive());
    }

    @Test
    void deactivationIsIdempotent() {
        UUID id = UUID.randomUUID();
        User user = new User(new Sector("Financeiro"), "Maria", "maria@exemplo.com", "hash", UserRole.MOTORISTA);
        user.deactivate();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> userService.deactivate(id));
        verify(userRepository, never()).lockActiveByRole(any());
    }

    @Test
    void reactivatesInactiveUser() {
        UUID id = UUID.randomUUID();
        User user = new User(new Sector("Financeiro"), "Maria", "maria@exemplo.com", "hash", UserRole.MOTORISTA);
        user.deactivate();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.reactivate(id);

        assertTrue(user.isActive());
    }

    @Test
    void reactivationIsIdempotent() {
        UUID id = UUID.randomUUID();
        User user = new User(new Sector("Financeiro"), "Maria", "maria@exemplo.com", "hash", UserRole.MOTORISTA);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> userService.reactivate(id));
        assertTrue(user.isActive());
    }

    @Test
    void throwsWhenReactivatingNonExistentUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.reactivate(id));
    }

    @Test
    void transfersDriverWithoutChangingRoleOrPassword() {
        UUID id = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        Sector svo = new Sector("Superintendência SVO");
        User driver = new User(new Sector("Lacen"), "Motorista", "driver@exemplo.com", "hash", UserRole.MOTORISTA);
        when(userRepository.findById(id)).thenReturn(Optional.of(driver));
        when(sectorRepository.findById(sectorId)).thenReturn(Optional.of(svo));

        User updated = userService.update(id, sectorId, driver.getName(), driver.getEmail(), null, UserRole.MOTORISTA, true);

        assertSame(svo, updated.getSector());
        assertEquals(UserRole.MOTORISTA, updated.getRole());
        assertEquals("hash", updated.getPassword());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changesOnlyRoleKeepingTheSameSector() {
        UUID id = UUID.randomUUID();
        UUID sectorId = UUID.randomUUID();
        Sector hemose = new Sector("Superintendência Hemose");
        User user = new User(hemose, "Pessoa", "pessoa@exemplo.com", "hash", UserRole.EMPLOYEE);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(sectorRepository.findById(sectorId)).thenReturn(Optional.of(hemose));

        User updated = userService.update(id, sectorId, user.getName(), user.getEmail(), null, UserRole.GESTOR_FROTA, true);

        assertSame(hemose, updated.getSector());
        assertEquals(UserRole.GESTOR_FROTA, updated.getRole());
    }

    @Test
    void missingSectorRejectsUpdateBeforeChangingAnyUserData() {
        UUID id = UUID.randomUUID();
        UUID missingSector = UUID.randomUUID();
        Sector original = new Sector("Lacen");
        User user = new User(original, "Pessoa", "pessoa@exemplo.com", "hash", UserRole.EMPLOYEE);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(sectorRepository.findById(missingSector)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.update(
                id, missingSector, "Alterado", "alterado@exemplo.com", "outra-senha", UserRole.ADMIN, false));

        assertSame(original, user.getSector());
        assertEquals(UserRole.EMPLOYEE, user.getRole());
        assertEquals("Pessoa", user.getName());
        assertEquals("pessoa@exemplo.com", user.getEmail());
        assertEquals("hash", user.getPassword());
        assertTrue(user.isActive());
        verifyNoInteractions(passwordEncoder);
    }
}
