package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.infra.repositories.TripRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TripUtilizationService {
    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final ZoneId reportingZone;

    public TripUtilizationService(
            TripRepository trips,
            VehicleRepository vehicles,
            @Value("${app.reporting.time-zone:America/Sao_Paulo}") String reportingTimeZone
    ) {
        this.trips = trips;
        this.vehicles = vehicles;
        try {
            this.reportingZone = ZoneId.of(reportingTimeZone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("app.reporting.time-zone deve ser um fuso IANA válido", exception);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateRate(UUID vehicleId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new InvalidRequestException("As datas inicial e final são obrigatórias");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidRequestException("A data final não pode ser anterior à data inicial");
        }
        if (!vehicles.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Veículo", vehicleId);
        }

        final Instant periodStart;
        final Instant periodEndExclusive;
        try {
            periodStart = startDate.atStartOfDay(reportingZone).toInstant();
            periodEndExclusive = endDate.plusDays(1).atStartOfDay(reportingZone).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("O período informado está fora do intervalo suportado");
        }

        long periodDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (periodDays <= 0) {
            throw new InvalidRequestException("O período deve conter ao menos um dia");
        }

        List<DateRange> occupiedRanges = trips.findCompletedIntervalsOverlappingPeriod(
                        vehicleId, periodStart, periodEndExclusive).stream()
                .map(interval -> occupiedDates(interval, startDate, endDate, periodStart, periodEndExclusive))
                .filter(range -> range != null)
                .sorted(Comparator.comparing(DateRange::start))
                .toList();

        long utilizedDays = countDistinctDays(occupiedRanges);
        return BigDecimal.valueOf(utilizedDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(periodDays), 2, RoundingMode.HALF_UP);
    }

    private DateRange occupiedDates(
            TripRepository.CompletedTripInterval interval,
            LocalDate startDate,
            LocalDate endDate,
            Instant periodStart,
            Instant periodEndExclusive
    ) {
        Instant clippedStart = interval.getStartedAt().isBefore(periodStart)
                ? periodStart : interval.getStartedAt();
        Instant clippedEnd = interval.getEndedAt().isAfter(periodEndExclusive)
                ? periodEndExclusive : interval.getEndedAt();
        if (!clippedStart.isBefore(clippedEnd)) {
            return null;
        }

        LocalDate firstDay = clippedStart.atZone(reportingZone).toLocalDate();
        LocalDate lastDay = clippedEnd.minusNanos(1).atZone(reportingZone).toLocalDate();
        if (firstDay.isBefore(startDate)) firstDay = startDate;
        if (lastDay.isAfter(endDate)) lastDay = endDate;
        return lastDay.isBefore(firstDay) ? null : new DateRange(firstDay, lastDay);
    }

    private long countDistinctDays(List<DateRange> ranges) {
        if (ranges.isEmpty()) return 0;

        long total = 0;
        LocalDate rangeStart = ranges.getFirst().start();
        LocalDate rangeEnd = ranges.getFirst().end();
        for (int i = 1; i < ranges.size(); i++) {
            DateRange next = ranges.get(i);
            if (!next.start().isAfter(rangeEnd)) {
                if (next.end().isAfter(rangeEnd)) rangeEnd = next.end();
            } else {
                total += ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1;
                rangeStart = next.start();
                rangeEnd = next.end();
            }
        }
        return total + ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1;
    }

    private record DateRange(LocalDate start, LocalDate end) { }
}
