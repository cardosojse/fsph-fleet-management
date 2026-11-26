package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.SvoOrderStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "svo_collection_orders")
public class SvoCollectionOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="request_id", unique=true, nullable=false)
    private SvoDeathRequest request;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="sector_id", nullable=false) private Sector sector;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="driver_id") private Driver driver;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="vehicle_id") private Vehicle vehicle;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="team_id") private SvoTeam team;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private SvoOrderStatus status = SvoOrderStatus.WAITING_ASSIGNMENT;
    @Column(name="requested_at",nullable=false,updatable=false) private Instant requestedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="scheduled_at") private Instant scheduledAt;
    @Column(name="planned_return_at") private Instant plannedReturnAt;
    @Column(name="collection_deadline_at") private Instant collectionDeadlineAt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="collected_at") private Instant collectedAt;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Version private long version;
    @Column(name="last_activity_at",nullable=false) private Instant lastActivityAt=Instant.now();
    protected SvoCollectionOrder() {}
    public SvoCollectionOrder(SvoDeathRequest request) {
        this.request=request; this.sector=request.getTriageSector();
        this.requestedAt=request.getCreatedAt(); this.createdAt=Instant.now();
    }
    public void assign(Driver driver, Vehicle vehicle, SvoTeam team, Instant start, Instant end, Instant deadline) {
        if(status!=SvoOrderStatus.WAITING_ASSIGNMENT && status!=SvoOrderStatus.ASSIGNED)
            throw new ConflictException("Atribuição só pode ser alterada antes do início");
        this.driver=driver; this.vehicle=vehicle; this.team=team; this.scheduledAt=start;
        this.plannedReturnAt=end; this.collectionDeadlineAt=deadline; this.status=SvoOrderStatus.ASSIGNED;
    }
    public void transition(SvoOrderStatus next, Instant now) {
        boolean valid = switch(status) {
            case WAITING_ASSIGNMENT, ASSIGNED -> next==SvoOrderStatus.CANCELED || (status==SvoOrderStatus.ASSIGNED && next==SvoOrderStatus.IN_PROGRESS);
            case IN_PROGRESS -> next==SvoOrderStatus.COLLECTED;
            case COLLECTED -> next==SvoOrderStatus.DELIVERED;
            default -> false;
        };
        if(!valid) throw new ConflictException("Transição inválida de "+status+" para "+next);
        if(now.isBefore(requestedAt)) throw new ConflictException("Horário anterior à solicitação");
        if(next==SvoOrderStatus.IN_PROGRESS) startedAt=now;
        if(next==SvoOrderStatus.COLLECTED) collectedAt=now;
        if(next==SvoOrderStatus.DELIVERED) {
            if(now.isBefore(collectedAt)) throw new ConflictException("Entrega anterior à coleta");
            deliveredAt=now;
        }
        status=next;
    }
    // Notes are writes too: advance the shared revision, even without a state change.
    public void touch() { lastActivityAt=Instant.now(); }
    public UUID getId(){return id;} public SvoDeathRequest getRequest(){return request;}
    public Sector getSector(){return sector;} public Driver getDriver(){return driver;}
    public Vehicle getVehicle(){return vehicle;} public SvoTeam getTeam(){return team;}
    public SvoOrderStatus getStatus(){return status;} public long getVersion(){return version;}
    public Instant getRequestedAt(){return requestedAt;} public Instant getCreatedAt(){return createdAt;}
    public Instant getScheduledAt(){return scheduledAt;} public Instant getPlannedReturnAt(){return plannedReturnAt;}
    public Instant getCollectionDeadlineAt(){return collectionDeadlineAt;} public Instant getStartedAt(){return startedAt;}
    public Instant getCollectedAt(){return collectedAt;} public Instant getDeliveredAt(){return deliveredAt;}
}
