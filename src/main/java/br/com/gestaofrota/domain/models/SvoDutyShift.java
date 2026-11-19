package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.SvoShiftStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "svo_duty_shifts")
public class SvoDutyShift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SvoTeam team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "shift_type", length = 50)
    private String shiftType;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SvoShiftStatus status = SvoShiftStatus.SCHEDULED;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SvoDutyShift() {
    }

    public SvoDutyShift(SvoTeam team, Vehicle vehicle, Instant startAt, Instant endAt, String shiftType, String notes) {
        this.team = Objects.requireNonNull(team, "Equipe é obrigatória");
        validateAndSetPeriod(startAt, endAt);
        this.vehicle = vehicle;
        this.shiftType = shiftType == null || shiftType.isBlank() ? null : shiftType.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.status = SvoShiftStatus.SCHEDULED;
    }

    public UUID getId() {
        return id;
    }

    public SvoTeam getTeam() {
        return team;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getShiftType() {
        return shiftType;
    }

    public String getNotes() {
        return notes;
    }

    public SvoShiftStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(SvoTeam team, Vehicle vehicle, Instant startAt, Instant endAt, String shiftType, String notes) {
        if (this.status == SvoShiftStatus.CANCELED || this.status == SvoShiftStatus.COMPLETED) {
            throw new ConflictException("Plantão cancelado ou concluído não pode ser alterado");
        }
        this.team = Objects.requireNonNull(team, "Equipe é obrigatória");
        validateAndSetPeriod(startAt, endAt);
        this.vehicle = vehicle;
        this.shiftType = shiftType == null || shiftType.isBlank() ? null : shiftType.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void cancel() {
        if (this.status == SvoShiftStatus.COMPLETED) {
            throw new ConflictException("Plantão concluído não pode ser cancelado");
        }
        this.status = SvoShiftStatus.CANCELED;
    }

    public void activate() {
        if (this.status != SvoShiftStatus.SCHEDULED) {
            throw new ConflictException("Apenas plantões agendados podem ser ativados");
        }
        this.status = SvoShiftStatus.ACTIVE;
    }

    public void complete() {
        if (this.status == SvoShiftStatus.CANCELED) {
            throw new ConflictException("Plantão cancelado não pode ser concluído");
        }
        this.status = SvoShiftStatus.COMPLETED;
    }

    private void validateAndSetPeriod(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new InvalidRequestException("Início e fim do plantão são obrigatórios");
        }
        if (!endAt.isAfter(startAt)) {
            throw new InvalidRequestException("O término do plantão deve ser posterior ao início");
        }
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
