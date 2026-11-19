package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.AuditLog;
import br.com.gestaofrota.infra.repositories.AuditLogRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTests {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should record audit log with explicit actor parameters")
    void recordWithExplicitActor() {
        UUID actorId = UUID.randomUUID();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        AuditLog log = auditLogService.record(
                actorId,
                "Admin User",
                "admin@fsph.se.gov.br",
                "ADMIN",
                "TRIP_REQUEST_APPROVED",
                "TRIP_REQUEST",
                "123",
                "Viagem aprovada",
                "192.168.1.50"
        );

        assertThat(log).isNotNull();
        assertThat(log.getActorUserId()).isEqualTo(actorId);
        assertThat(log.getActorName()).isEqualTo("Admin User");
        assertThat(log.getActorEmail()).isEqualTo("admin@fsph.se.gov.br");
        assertThat(log.getActorRole()).isEqualTo("ADMIN");
        assertThat(log.getAction()).isEqualTo("TRIP_REQUEST_APPROVED");
        assertThat(log.getResourceType()).isEqualTo("TRIP_REQUEST");
        assertThat(log.getResourceId()).isEqualTo("123");
        assertThat(log.getDetails()).isEqualTo("Viagem aprovada");
        assertThat(log.getClientIp()).isEqualTo("192.168.1.50");
        assertThat(log.getOccurredAt()).isNotNull();

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should automatically extract actor from SecurityContext when omitted")
    void recordExtractingActorFromSecurityContext() {
        UUID actorId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                actorId,
                UUID.randomUUID(),
                "Gestor Frota",
                "gestor@fsph.se.gov.br",
                "secret",
                UserRole.GESTOR_FROTA,
                true,
                Set.of(Permission.AUDIT_LOGS_READ)
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        AuditLog log = auditLogService.recordCurrent(
                "VEHICLE_UPDATED",
                "VEHICLE",
                "456",
                "Veículo atualizado"
        );

        assertThat(log.getActorUserId()).isEqualTo(actorId);
        assertThat(log.getActorName()).isEqualTo("Gestor Frota");
        assertThat(log.getActorEmail()).isEqualTo("gestor@fsph.se.gov.br");
        assertThat(log.getActorRole()).isEqualTo("GESTOR_FROTA");
        assertThat(log.getAction()).isEqualTo("VEHICLE_UPDATED");
        assertThat(log.getResourceType()).isEqualTo("VEHICLE");
    }

    @Test
    @DisplayName("Should query audit logs with pagination and filters")
    void queryAuditLogsWithFilters() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        UUID actorId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 10);

        AuditLog log = new AuditLog(actorId, "Admin", "admin@fsph.se.gov.br", "ADMIN", "LOGIN", "AUTH", null, "Login ok", null);
        Page<AuditLog> pageResult = new PageImpl<>(List.of(log), pageable, 1);

        when(auditLogRepository.findFiltered(from, to, actorId, "LOGIN", "AUTH", pageable))
                .thenReturn(pageResult);

        Page<AuditLog> result = auditLogService.listLogs(from, to, actorId, "LOGIN", "AUTH", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(auditLogRepository).findFiltered(from, to, actorId, "LOGIN", "AUTH", pageable);
    }
}
