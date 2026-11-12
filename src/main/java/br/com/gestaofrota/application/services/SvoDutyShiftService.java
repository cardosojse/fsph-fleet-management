package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.responses.SvoCoverageGapResponse;
import br.com.gestaofrota.api.v1.dtos.responses.SvoCoverageReportResponse;
import br.com.gestaofrota.domain.enums.SvoShiftStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.SvoDutyShift;
import br.com.gestaofrota.domain.models.SvoTeam;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.SvoDutyShiftRepository;
import br.com.gestaofrota.infra.repositories.SvoTeamRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class SvoDutyShiftService {

    private static final Set<SvoShiftStatus> ACTIVE_SHIFT_STATUSES =
            EnumSet.of(SvoShiftStatus.SCHEDULED, SvoShiftStatus.ACTIVE);

    private final SvoDutyShiftRepository shiftRepository;
    private final SvoTeamRepository teamRepository;
    private final VehicleRepository vehicleRepository;
    private final MaintenanceAvailabilityGuard maintenanceGuard;
    private final AuditLogService auditLogService;

    public SvoDutyShiftService(
            SvoDutyShiftRepository shiftRepository,
            SvoTeamRepository teamRepository,
            VehicleRepository vehicleRepository,
            MaintenanceAvailabilityGuard maintenanceGuard,
            AuditLogService auditLogService
    ) {
        this.shiftRepository = shiftRepository;
        this.teamRepository = teamRepository;
        this.vehicleRepository = vehicleRepository;
        this.maintenanceGuard = maintenanceGuard;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SvoDutyShift create(
            UUID teamId,
            UUID vehicleId,
            Instant startAt,
            Instant endAt,
            String shiftType,
            String notes,
            AuthenticatedUser actor
    ) {
        validatePeriod(startAt, endAt);
        SvoTeam team = findTeam(teamId);
        if (!team.isActive()) {
            throw new ConflictException("Equipe SVO inativa não pode ser escalada para plantão");
        }

        Vehicle vehicle = resolveAndValidateVehicle(vehicleId, startAt, endAt, null);
        assertNoTeamShiftOverlap(teamId, startAt, endAt, null);

        SvoDutyShift shift = new SvoDutyShift(team, vehicle, startAt, endAt, shiftType, notes);
        SvoDutyShift saved = shiftRepository.save(shift);

        auditLogService.record(actor, "SVO_SHIFT_CREATED", "SVO_SHIFT", saved.getId().toString(),
                "Plantão SVO agendado para equipe '" + team.getName() + "' de " + startAt + " a " + endAt);
        return saved;
    }

    @Transactional
    public SvoDutyShift update(
            UUID id,
            UUID teamId,
            UUID vehicleId,
            Instant startAt,
            Instant endAt,
            String shiftType,
            String notes,
            AuthenticatedUser actor
    ) {
        validatePeriod(startAt, endAt);
        SvoDutyShift shift = find(id);
        SvoTeam team = findTeam(teamId);
        if (!team.isActive()) {
            throw new ConflictException("Equipe SVO inativa não pode ser escalada para plantão");
        }

        Vehicle vehicle = resolveAndValidateVehicle(vehicleId, startAt, endAt, id);
        assertNoTeamShiftOverlap(teamId, startAt, endAt, id);

        shift.update(team, vehicle, startAt, endAt, shiftType, notes);
        SvoDutyShift updated = shiftRepository.save(shift);

        auditLogService.record(actor, "SVO_SHIFT_UPDATED", "SVO_SHIFT", updated.getId().toString(),
                "Plantão SVO atualizado para equipe '" + team.getName() + "' de " + startAt + " a " + endAt);
        return updated;
    }

    @Transactional
    public SvoDutyShift cancel(UUID id, AuthenticatedUser actor) {
        SvoDutyShift shift = find(id);
        shift.cancel();
        SvoDutyShift canceled = shiftRepository.save(shift);

        auditLogService.record(actor, "SVO_SHIFT_CANCELED", "SVO_SHIFT", canceled.getId().toString(),
                "Plantão SVO cancelado para equipe '" + shift.getTeam().getName() + "'");
        return canceled;
    }

    @Transactional(readOnly = true)
    public List<SvoDutyShift> list(Instant from, Instant to, UUID teamId, SvoShiftStatus status) {
        return shiftRepository.findShifts(from, to, teamId, status);
    }

    @Transactional(readOnly = true)
    public SvoDutyShift find(UUID id) {
        return shiftRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantão SVO", id));
    }

    @Transactional(readOnly = true)
    public List<SvoDutyShift> getCurrentShifts(Instant at) {
        Instant effectiveAt = at != null ? at : Instant.now();
        return shiftRepository.findCurrentShifts(effectiveAt, ACTIVE_SHIFT_STATUSES);
    }

    @Transactional(readOnly = true)
    public SvoCoverageReportResponse findCoverageGaps(Instant from, Instant to) {
        validatePeriod(from, to);

        List<SvoDutyShift> coveringShifts = shiftRepository.findCoveringShifts(ACTIVE_SHIFT_STATUSES, from, to);

        if (coveringShifts.isEmpty()) {
            long durationMinutes = Duration.between(from, to).toMinutes();
            double durationHours = roundHours(durationMinutes);
            SvoCoverageGapResponse fullGap = new SvoCoverageGapResponse(from, to, durationMinutes, durationHours);
            return new SvoCoverageReportResponse(from, to, false, 1, durationMinutes, durationHours, List.of(fullGap));
        }

        // Clip and merge intervals
        List<TimeInterval> intervals = new ArrayList<>();
        for (SvoDutyShift s : coveringShifts) {
            Instant start = s.getStartAt().isBefore(from) ? from : s.getStartAt();
            Instant end = s.getEndAt().isAfter(to) ? to : s.getEndAt();
            if (end.isAfter(start)) {
                intervals.add(new TimeInterval(start, end));
            }
        }

        intervals.sort(Comparator.comparing(TimeInterval::start));

        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = intervals.getFirst();
        for (int i = 1; i < intervals.size(); i++) {
            TimeInterval next = intervals.get(i);
            if (!next.start().isAfter(current.end())) {
                Instant maxEnd = current.end().isAfter(next.end()) ? current.end() : next.end();
                current = new TimeInterval(current.start(), maxEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        List<SvoCoverageGapResponse> gaps = new ArrayList<>();
        if (merged.getFirst().start().isAfter(from)) {
            gaps.add(createGap(from, merged.getFirst().start()));
        }

        for (int i = 0; i < merged.size() - 1; i++) {
            Instant gapStart = merged.get(i).end();
            Instant gapEnd = merged.get(i + 1).start();
            if (gapEnd.isAfter(gapStart)) {
                gaps.add(createGap(gapStart, gapEnd));
            }
        }

        if (merged.getLast().end().isBefore(to)) {
            gaps.add(createGap(merged.getLast().end(), to));
        }

        long totalUncoveredMinutes = gaps.stream().mapToLong(SvoCoverageGapResponse::durationMinutes).sum();
        double totalUncoveredHours = roundHours(totalUncoveredMinutes);

        return new SvoCoverageReportResponse(
                from,
                to,
                gaps.isEmpty(),
                gaps.size(),
                totalUncoveredMinutes,
                totalUncoveredHours,
                gaps
        );
    }

    private static SvoCoverageGapResponse createGap(Instant start, Instant end) {
        long minutes = Duration.between(start, end).toMinutes();
        return new SvoCoverageGapResponse(start, end, minutes, roundHours(minutes));
    }

    private static double roundHours(long minutes) {
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void validatePeriod(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new InvalidRequestException("Data/hora de início e término são obrigatórias");
        }
        if (!endAt.isAfter(startAt)) {
            throw new InvalidRequestException("A data/hora de término deve ser posterior ao início");
        }
    }

    private SvoTeam findTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipe SVO", teamId));
    }

    private Vehicle resolveAndValidateVehicle(UUID vehicleId, Instant startAt, Instant endAt, UUID currentShiftId) {
        if (vehicleId == null) {
            return null;
        }
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", vehicleId));

        if (!vehicle.isActive()) {
            throw new ConflictException("Veículo inativo não pode ser escalado para plantão");
        }
        maintenanceGuard.assertVehicleAvailableForAllocation(vehicleId);

        boolean hasOverlap = shiftRepository.findVehicleShiftsOverlapping(vehicleId, ACTIVE_SHIFT_STATUSES, startAt, endAt)
                .stream()
                .anyMatch(s -> currentShiftId == null || !s.getId().equals(currentShiftId));

        if (hasOverlap) {
            throw new ConflictException("O veículo informado já está escalado para outro plantão no período");
        }
        return vehicle;
    }

    private void assertNoTeamShiftOverlap(UUID teamId, Instant startAt, Instant endAt, UUID currentShiftId) {
        boolean hasOverlap = shiftRepository.findTeamShiftsOverlapping(teamId, ACTIVE_SHIFT_STATUSES, startAt, endAt)
                .stream()
                .anyMatch(s -> currentShiftId == null || !s.getId().equals(currentShiftId));

        if (hasOverlap) {
            throw new ConflictException("A equipe SVO já possui plantão agendado para o período informado");
        }
    }

    private record TimeInterval(Instant start, Instant end) {
    }
}
