package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.SvoShiftStatus;
import br.com.gestaofrota.domain.models.SvoDutyShift;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SvoDutyShiftResponse(
        UUID id,
        UUID teamId,
        String teamName,
        List<SvoTeamMemberResponse> teamMembers,
        UUID vehicleId,
        String vehiclePlate,
        String vehicleModel,
        Instant startAt,
        Instant endAt,
        String shiftType,
        String notes,
        SvoShiftStatus status,
        Instant createdAt
) {
    public static SvoDutyShiftResponse from(SvoDutyShift shift) {
        return new SvoDutyShiftResponse(
                shift.getId(),
                shift.getTeam().getId(),
                shift.getTeam().getName(),
                shift.getTeam().getMembers() == null
                        ? List.of()
                        : shift.getTeam().getMembers().stream().map(SvoTeamMemberResponse::from).toList(),
                shift.getVehicle() != null ? shift.getVehicle().getId() : null,
                shift.getVehicle() != null ? shift.getVehicle().getPlate() : null,
                shift.getVehicle() != null ? shift.getVehicle().getModel() : null,
                shift.getStartAt(),
                shift.getEndAt(),
                shift.getShiftType(),
                shift.getNotes(),
                shift.getStatus(),
                shift.getCreatedAt()
        );
    }
}
