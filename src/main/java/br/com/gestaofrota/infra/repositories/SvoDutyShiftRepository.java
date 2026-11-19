package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.SvoShiftStatus;
import br.com.gestaofrota.domain.models.SvoDutyShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SvoDutyShiftRepository extends JpaRepository<SvoDutyShift, UUID> {

    @Query("SELECT s FROM SvoDutyShift s " +
           "LEFT JOIN FETCH s.team t " +
           "LEFT JOIN FETCH t.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH s.vehicle " +
           "WHERE s.id = :id")
    Optional<SvoDutyShift> findWithDetailsById(@Param("id") UUID id);

    @Query("SELECT s FROM SvoDutyShift s " +
           "LEFT JOIN FETCH s.team t " +
           "LEFT JOIN FETCH t.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH s.vehicle " +
           "WHERE (:teamId IS NULL OR s.team.id = :teamId) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (cast(:from as timestamp) IS NULL OR s.endAt > :from) " +
           "AND (cast(:to as timestamp) IS NULL OR s.startAt < :to) " +
           "ORDER BY s.startAt ASC")
    List<SvoDutyShift> findShifts(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("teamId") UUID teamId,
            @Param("status") SvoShiftStatus status
    );

    @Query("SELECT s FROM SvoDutyShift s " +
           "LEFT JOIN FETCH s.team t " +
           "LEFT JOIN FETCH t.members m " +
           "LEFT JOIN FETCH m.user " +
           "LEFT JOIN FETCH s.vehicle " +
           "WHERE s.startAt <= :at AND s.endAt > :at " +
           "AND s.status IN :activeStatuses " +
           "AND s.team.active = true " +
           "ORDER BY s.startAt ASC")
    List<SvoDutyShift> findCurrentShifts(
            @Param("at") Instant at,
            @Param("activeStatuses") Collection<SvoShiftStatus> activeStatuses
    );

    @Query("SELECT s FROM SvoDutyShift s " +
           "LEFT JOIN FETCH s.team " +
           "WHERE s.status IN :activeStatuses " +
           "AND s.team.active = true " +
           "AND s.startAt < :to AND s.endAt > :from " +
           "ORDER BY s.startAt ASC")
    List<SvoDutyShift> findCoveringShifts(
            @Param("activeStatuses") Collection<SvoShiftStatus> activeStatuses,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("SELECT s FROM SvoDutyShift s " +
           "WHERE s.team.id = :teamId " +
           "AND s.status IN :activeStatuses " +
           "AND s.startAt < :endAt AND s.endAt > :startAt")
    List<SvoDutyShift> findTeamShiftsOverlapping(
            @Param("teamId") UUID teamId,
            @Param("activeStatuses") Collection<SvoShiftStatus> activeStatuses,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("SELECT s FROM SvoDutyShift s " +
           "WHERE s.vehicle.id = :vehicleId " +
           "AND s.status IN :activeStatuses " +
           "AND s.startAt < :endAt AND s.endAt > :startAt")
    List<SvoDutyShift> findVehicleShiftsOverlapping(
            @Param("vehicleId") UUID vehicleId,
            @Param("activeStatuses") Collection<SvoShiftStatus> activeStatuses,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );
}
