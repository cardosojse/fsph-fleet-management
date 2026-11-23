package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.requests.FuelingReportFilterRequest;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportItemResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportResponse;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.exceptions.UnauthorizedAccessException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.infra.repositories.FuelingRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scope uses the vehicle's current sector, not its sector at the fueling date.
 * Transferring a vehicle changes visibility of all its historical fuelings. */
@Service
public class FuelingReportQueryService {
    private final FuelingRepository repository;
    private final FuelingReportService reports;
    private final ReportLimits limits;

    public FuelingReportQueryService(FuelingRepository repository, FuelingReportService reports, ReportLimits limits) {
        this.repository = repository;
        this.reports = reports;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public FuelingReportResponse query(AuthenticatedUser user, FuelingReportFilterRequest filters) {
        if (user == null || !user.active() || !user.permissions().contains(Permission.FUELINGS_READ)
                || (user.role() != UserRole.ADMIN && user.role() != UserRole.GESTOR_FROTA)
                || (user.role() == UserRole.GESTOR_FROTA && user.sectorId() == null)) {
            throw new UnauthorizedAccessException();
        }
        if (filters == null) {
            throw new InvalidRequestException("Filtros são obrigatórios");
        }
        boolean global = user.role() == UserRole.ADMIN;
        if (filters.vehicleId() != null
                && !repository.existsReportVehicle(filters.vehicleId(), global, user.sectorId())) {
            throw new ResourceNotFoundException("Veículo", filters.vehicleId());
        }
        // Fetch one extra record relative to the data allowance (one row is reserved for totals).
        // The assembler rejects overflow instead of returning a truncated report.
        var records = repository.findForReport(filters.startDate(), filters.endDate(), filters.vehicleId(),
                global, user.sectorId(), PageRequest.of(0, limits.maxRows()));
        var items = records.stream().map(f -> new FuelingReportItemResponse(f.getId(), f.getFueledOn(),
                f.getVehicle().getId(), f.getVehicle().getPlate(), f.getVehicle().getModel(),
                f.getFuelType(), f.getLiters(), f.getTotalValue())).toList();
        return reports.build(filters, items);
    }
}
