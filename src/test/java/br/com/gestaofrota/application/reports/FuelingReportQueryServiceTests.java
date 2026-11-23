package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.requests.FuelingReportFilterRequest;
import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.exceptions.*;
import br.com.gestaofrota.infra.repositories.FuelingRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FuelingReportQueryServiceTests {
    private final FuelingRepository repository = mock(FuelingRepository.class);
    private final ReportLimits limits = new ReportLimits(4, 20, 2000, 1000000);
    private final FuelingReportQueryService service = new FuelingReportQueryService(repository,
            new FuelingReportService(limits), limits);
    private final LocalDate date = LocalDate.of(2026, 1, 15);
    private final FuelingReportFilterRequest filters = new FuelingReportFilterRequest(date, date, null);

    private AuthenticatedUser principal(UserRole role, UUID sector, boolean read) {
        return new AuthenticatedUser(UUID.randomUUID(), sector, "Teste", "teste@example.com", "hash", role,
                true, read ? Set.of(Permission.FUELINGS_READ) : Set.of());
    }

    @Test
    void deniesUnsupportedRolesMissingPermissionAndMissingSectorBeforeDatabaseAccess() {
        assertThrows(UnauthorizedAccessException.class, () -> service.query(null, filters));
        for (var role : UserRole.values()) {
            assertThrows(UnauthorizedAccessException.class,
                    () -> service.query(principal(role, UUID.randomUUID(), false), filters));
            if (role != UserRole.ADMIN && role != UserRole.GESTOR_FROTA)
                assertThrows(UnauthorizedAccessException.class,
                        () -> service.query(principal(role, UUID.randomUUID(), true), filters));
        }
        assertThrows(UnauthorizedAccessException.class,
                () -> service.query(principal(UserRole.GESTOR_FROTA, null, true), filters));
        verifyNoInteractions(repository);
    }

    @Test
    void passesScopeAndBoundedFetchToRepositoryAndReturnsEmptyTotals() {
        UUID sector = UUID.randomUUID();
        when(repository.findForReport(date, date, null, false, sector, PageRequest.of(0, 4))).thenReturn(List.of());
        var report = service.query(principal(UserRole.GESTOR_FROTA, sector, true), filters);
        assertEquals(0, report.totals().totalFuelings());
        verify(repository).findForReport(date, date, null, false, sector, PageRequest.of(0, 4));
        when(repository.findForReport(date, date, null, true, null, PageRequest.of(0, 4))).thenReturn(List.of());
        service.query(principal(UserRole.ADMIN, null, true), filters);
        verify(repository).findForReport(date, date, null, true, null, PageRequest.of(0, 4));
    }

    @Test
    void returnsSameNotFoundForEveryVehicleAbsentFromAuthorizedScope() {
        UUID sector = UUID.randomUUID();
        var manager = principal(UserRole.GESTOR_FROTA, sector, true);
        for (UUID id : List.of(UUID.randomUUID(), UUID.randomUUID())) {
            var exception = assertThrows(ResourceNotFoundException.class,
                    () -> service.query(manager, new FuelingReportFilterRequest(date, date, id)));
            assertEquals(new ResourceNotFoundException("Veículo", id).getMessage(), exception.getMessage());
            verify(repository).existsReportVehicle(id, false, sector);
        }
        verify(repository, never()).findForReport(any(), any(), any(), anyBoolean(), any(), any());
    }
}
