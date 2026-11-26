package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.*;
import br.com.gestaofrota.api.v1.dtos.responses.*;
import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.exceptions.*;
import br.com.gestaofrota.domain.models.*;
import br.com.gestaofrota.infra.repositories.*;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static br.com.gestaofrota.domain.enums.Permission.*;
import static br.com.gestaofrota.domain.enums.SvoOrderStatus.*;

@Service
@Transactional(readOnly=true)
public class SvoCollectionOrderService {
    private final SvoCollectionOrderRepository orders;
    private final SvoOrderEventRepository events;
    private final SvoDeathRequestRepository requests;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final SvoTeamRepository teams;
    private final UserRepository users;
    private final MaintenanceAvailabilityGuard maintenance;
    public SvoCollectionOrderService(SvoCollectionOrderRepository orders,SvoOrderEventRepository events,
            SvoDeathRequestRepository requests,DriverRepository drivers,VehicleRepository vehicles,
            SvoTeamRepository teams,UserRepository users,MaintenanceAvailabilityGuard maintenance){
        this.orders=orders;this.events=events;this.requests=requests;this.drivers=drivers;
        this.vehicles=vehicles;this.teams=teams;this.users=users;this.maintenance=maintenance;
    }
    private boolean global(AuthenticatedUser a){return a.role()==UserRole.ADMIN||a.role()==UserRole.GESTOR_FROTA;}
    private boolean operator(AuthenticatedUser a){return a.role()==UserRole.MOTORISTA||a.role()==UserRole.EQUIPE_COLETA_SVO;}
    private void scope(AuthenticatedUser a,SvoCollectionOrder o){
        TravelAccess.require(a,SVO_ORDERS_READ);
        if(!global(a)&&!Objects.equals(a.sectorId(),o.getSector().getId()))throw new UnauthorizedAccessException();
        if(operator(a)&&!assigned(a,o))throw new UnauthorizedAccessException();
        if(!global(a)&&!operator(a)&&a.role()!=UserRole.ATENDENTE_SVO&&a.role()!=UserRole.GESTOR_SVO)
            throw new UnauthorizedAccessException();
    }
    private boolean assigned(AuthenticatedUser a,SvoCollectionOrder o){
        return (o.getDriver()!=null && o.getDriver().isActive() && o.getDriver().getUser().isActive()
                && o.getDriver().getUser().getRole()==UserRole.MOTORISTA && o.getDriver().getUser().getId().equals(a.id()))
            || (a.role()==UserRole.EQUIPE_COLETA_SVO && o.getTeam()!=null && o.getTeam().isActive()
                && o.getTeam().getMembers().stream().anyMatch(m->m.isActive()&&m.getUser().isActive()&&m.getUser().getId().equals(a.id())));
    }
    private void manager(AuthenticatedUser a,SvoCollectionOrder o){
        scope(a,o);TravelAccess.require(a,SVO_ORDERS_MANAGE);
        if(!global(a)&&a.role()!=UserRole.GESTOR_SVO)throw new UnauthorizedAccessException();
    }
    private boolean executor(AuthenticatedUser a,SvoCollectionOrder o){
        return TravelAccess.has(a,SVO_ORDERS_EXECUTE)&&(a.role()==UserRole.ADMIN||(operator(a)&&assigned(a,o)));
    }
    private List<SvoOrderStatus> transitions(AuthenticatedUser a,SvoCollectionOrder o){
        var result=new ArrayList<SvoOrderStatus>();
        if(executor(a,o)) switch(o.getStatus()){
            case ASSIGNED -> result.add(IN_PROGRESS);
            case IN_PROGRESS -> result.add(COLLECTED);
            case COLLECTED -> result.add(DELIVERED);
            default -> {}
        }
        if(TravelAccess.has(a,SVO_ORDERS_MANAGE)&&(global(a)||a.role()==UserRole.GESTOR_SVO)
                &&(o.getStatus()==WAITING_ASSIGNMENT||o.getStatus()==ASSIGNED))result.add(CANCELED);
        return result;
    }
    private SvoOrderResponse response(AuthenticatedUser a,SvoCollectionOrder o,boolean history){
        return SvoOrderResponse.from(o,transitions(a,o),history?events.findByOrderIdOrderByOrderVersionAscOccurredAtAsc(o.getId())
                .stream().map(SvoOrderEventResponse::from).toList():List.of(),Instant.now());
    }
    private SvoCollectionOrder findOrder(UUID id){return orders.findById(id).orElseThrow(()->new ResourceNotFoundException("Ordem SVO",id));}
    private SvoCollectionOrder lock(AuthenticatedUser a,UUID id,long version){
        var o=orders.lockById(id).orElseThrow(()->new ResourceNotFoundException("Ordem SVO",id));scope(a,o);
        if(o.getVersion()!=version)throw new ConflictException("A ordem foi alterada por outra pessoa. Atualize antes de salvar novamente.");
        return o;
    }
    private void event(AuthenticatedUser a,SvoCollectionOrder o,String type,String text,SvoOrderStatus previous){
        var author=users.findById(a.id()).orElseThrow(()->new UnauthorizedAccessException());
        if(!author.isActive())throw new UnauthorizedAccessException();
        orders.flush();events.saveAndFlush(new SvoOrderEvent(o,author,type,text,previous,Instant.now()));
    }
    @Transactional
    public SvoOrderResponse generate(AuthenticatedUser a,UUID requestId){
        TravelAccess.require(a,SVO_REQUESTS_MANAGE);TravelAccess.require(a,SVO_ORDERS_READ);
        if(!global(a)&&a.role()!=UserRole.ATENDENTE_SVO&&a.role()!=UserRole.GESTOR_SVO)throw new UnauthorizedAccessException();
        var source=requests.lockById(requestId).orElseThrow(()->new ResourceNotFoundException("Solicitação SVO",requestId));
        var existing=orders.findByRequestId(requestId);
        if(existing.isPresent()){scope(a,existing.get());return response(a,existing.get(),true);}
        if(source.getStatus()!=SvoRequestStatus.TRIAGED)throw new ConflictException("Apenas solicitações aprovadas podem gerar coleta");
        if(!global(a)&&!Objects.equals(a.sectorId(),source.getTriageSector().getId()))throw new UnauthorizedAccessException();
        var order=orders.saveAndFlush(new SvoCollectionOrder(source));source.markOrderGenerated();
        event(a,order,"CREATED","Ordem gerada a partir da triagem aprovada",null);
        return response(a,order,true);
    }
    public SvoOrderResponse find(AuthenticatedUser a,UUID id){var o=findOrder(id);scope(a,o);return response(a,o,true);}
    private Specification<SvoCollectionOrder> filter(AuthenticatedUser a,SvoOrderStatus status,String search,Instant from,Instant to,UUID teamId,UUID vehicleId,UUID requestId){
        TravelAccess.require(a,SVO_ORDERS_READ);
        if(!global(a)&&!operator(a)&&a.role()!=UserRole.ATENDENTE_SVO&&a.role()!=UserRole.GESTOR_SVO)throw new UnauthorizedAccessException();
        if(from!=null&&to!=null&&!to.isAfter(from))throw new InvalidRequestException("Período inválido");
        return (root,q,cb)->{
            var p=new ArrayList<Predicate>();
            if(!global(a))p.add(cb.equal(root.get("sector").get("id"),a.sectorId()));
            if(operator(a)){
                var member=q.subquery(UUID.class);var m=member.from(SvoTeamMember.class);
                member.select(m.get("team").get("id")).where(cb.equal(m.get("user").get("id"),a.id()),cb.isTrue(m.get("active")),cb.isTrue(m.get("user").get("active")));
                var assignedDriver=cb.equal(root.join("driver",jakarta.persistence.criteria.JoinType.LEFT).get("user").get("id"),a.id());
                p.add(a.role()==UserRole.EQUIPE_COLETA_SVO?cb.or(assignedDriver,cb.and(root.get("team").get("id").in(member),cb.isTrue(root.get("team").get("active")))):assignedDriver);
            }
            if(status!=null)p.add(cb.equal(root.get("status"),status));
            if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("requestedAt"),from));
            if(to!=null)p.add(cb.lessThan(root.get("requestedAt"),to));
            if(teamId!=null)p.add(cb.equal(root.get("team").get("id"),teamId));
            if(vehicleId!=null)p.add(cb.equal(root.get("vehicle").get("id"),vehicleId));
            if(requestId!=null)p.add(cb.equal(root.get("request").get("id"),requestId));
            if(search!=null&&!search.isBlank()){
                String term="%"+search.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
                p.add(cb.or(cb.like(cb.lower(root.get("request").get("protocolNumber")),term,'\\'),cb.like(cb.lower(root.get("request").get("address")),term,'\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
    public PageResponse<SvoOrderResponse> list(AuthenticatedUser a,SvoOrderStatus status,String search,Instant from,Instant to,UUID teamId,UUID vehicleId,UUID requestId,int page,int size){
        return PageResponse.from(orders.findAll(filter(a,status,search,from,to,teamId,vehicleId,requestId),TravelAccess.page(page,size)).map(o->response(a,o,false)));
    }
    @Transactional
    public SvoOrderResponse assign(AuthenticatedUser a,UUID id,AssignSvoOrderRequest r){
        var o=lock(a,id,r.expectedVersion());manager(a,o);
        var schedule=RouteSchedule.resolve(r.scheduledAt(),null,r.plannedReturnAt());
        if(schedule.plannedStartAt().isBefore(o.getRequestedAt()))throw new InvalidRequestException("Agendamento anterior à solicitação");
        if(r.collectionDeadlineAt()!=null&&r.collectionDeadlineAt().isBefore(o.getRequestedAt()))throw new InvalidRequestException("Prazo anterior à solicitação");
        // Same resource lock order as the existing fleet allocation paths.
        var v=vehicles.lockById(r.vehicleId()).orElseThrow(()->new ResourceNotFoundException("Veículo",r.vehicleId()));
        var d=drivers.lockById(r.driverId()).orElseThrow(()->new ResourceNotFoundException("Motorista",r.driverId()));
        if(!v.isAvailableForAllocation()||!d.isActive()||!d.getUser().isActive()||d.getUser().getRole()!=UserRole.MOTORISTA)
            throw new ConflictException("Motorista e veículo devem estar ativos e disponíveis");
        maintenance.assertVehicleAvailableForAllocation(v.getId());
        if(v.getSector()==null||!v.getSector().getId().equals(o.getSector().getId())||!d.getUser().getSector().getId().equals(o.getSector().getId()))
            throw new ConflictException("Use motorista e veículo do setor responsável pela coleta");
        SvoTeam team=r.teamId()==null?null:teams.findWithMembersById(r.teamId()).orElseThrow(()->new ResourceNotFoundException("Equipe SVO",r.teamId()));
        if(team!=null&&(!team.isActive()||team.getMembers().stream().noneMatch(m->m.isActive()&&m.getUser().isActive())
                ||team.getMembers().stream().anyMatch(m->m.isActive()&&!m.getUser().getSector().getId().equals(o.getSector().getId()))))
            throw new ConflictException("Equipe deve estar ativa, com membros do setor responsável");
        var previous=o.getStatus();o.assign(d,v,team,r.scheduledAt(),r.plannedReturnAt(),r.collectionDeadlineAt());o.touch();
        event(a,o,"ASSIGNED","Motorista: "+d.getUser().getName()+"; veículo: "+v.getPlate()+"; equipe: "+(team==null?"Sem equipe":team.getName())
                +"; agendamento: "+r.scheduledAt()+"; retorno: "+r.plannedReturnAt()+"; prazo de coleta: "+(r.collectionDeadlineAt()==null?"não definido":r.collectionDeadlineAt()),previous);
        return response(a,o,true);
    }
    @Transactional
    public SvoOrderResponse transition(AuthenticatedUser a,UUID id,TransitionSvoOrderRequest r){
        var o=lock(a,id,r.expectedVersion());
        if(r.status()==CANCELED)manager(a,o);else if(!executor(a,o))throw new UnauthorizedAccessException();
        if(!transitions(a,o).contains(r.status()))throw new ConflictException("Transição inválida para a etapa atual");
        if(r.status()==IN_PROGRESS){
            var v=vehicles.lockById(o.getVehicle().getId()).orElseThrow();
            var d=drivers.lockById(o.getDriver().getId()).orElseThrow();
            if(!v.isAvailableForAllocation()||!d.isActive()||!d.getUser().isActive()||d.getUser().getRole()!=UserRole.MOTORISTA)
                throw new ConflictException("Recursos indisponíveis para iniciar");
            maintenance.assertVehicleAvailableForAllocation(v.getId());
        }
        var previous=o.getStatus();o.transition(r.status(),Instant.now());o.touch();
        event(a,o,"STATUS_CHANGED",r.observation()==null?null:r.observation().trim(),previous);return response(a,o,true);
    }
    @Transactional
    public SvoOrderResponse note(AuthenticatedUser a,UUID id,AddSvoOrderNoteRequest r){
        var o=lock(a,id,r.expectedVersion());TravelAccess.require(a,SVO_ORDERS_NOTES);
        if(a.role()!=UserRole.ADMIN&&a.role()!=UserRole.ATENDENTE_SVO&&a.role()!=UserRole.GESTOR_SVO)throw new UnauthorizedAccessException();
        o.touch();event(a,o,"NOTE",r.text().trim(),o.getStatus());return response(a,o,true);
    }
    public SvoOrderResourcesResponse resources(AuthenticatedUser a,UUID id){
        var o=findOrder(id);manager(a,o);UUID sector=o.getSector().getId();
        var ds=drivers.findAll().stream().filter(d->d.isActive()&&d.getUser().isActive()&&d.getUser().getRole()==UserRole.MOTORISTA&&d.getUser().getSector().getId().equals(sector))
                .map(d->new SvoOrderResourcesResponse.Option(d.getId(),d.getUser().getName())).toList();
        var vs=vehicles.findAll().stream().filter(v->v.isAvailableForAllocation()&&v.getSector()!=null&&v.getSector().getId().equals(sector))
                .map(v->new SvoOrderResourcesResponse.Option(v.getId(),v.getPlate()+" — "+v.getModel())).toList();
        var ts=teams.findByActiveTrueOrderByNameAsc().stream().filter(t->t.getMembers().stream().anyMatch(m->m.isActive()&&m.getUser().isActive())&&t.getMembers().stream().noneMatch(m->m.isActive()&&!m.getUser().getSector().getId().equals(sector)))
                .map(t->new SvoOrderResourcesResponse.Option(t.getId(),t.getName())).toList();
        return new SvoOrderResourcesResponse(ds,vs,ts);
    }
    public SvoOrderReportResponse report(AuthenticatedUser a,Instant from,Instant to,UUID teamId,UUID vehicleId){
        TravelAccess.require(a,SVO_ORDERS_REPORT);
        if(!global(a)&&a.role()!=UserRole.GESTOR_SVO)throw new UnauthorizedAccessException();
        var rows=orders.findAll(filter(a,null,null,from,to,teamId,vehicleId,null),org.springframework.data.domain.Sort.by("requestedAt").descending())
                .stream().map(o->response(a,o,false)).toList();
        long collected=rows.stream().filter(o->o.collectedAt()!=null).count(),delivered=rows.stream().filter(o->o.deliveredAt()!=null).count();
        return new SvoOrderReportResponse(rows.size(),collected,delivered,average(rows,0),average(rows,1),average(rows,2),rows);
    }
    private Double average(List<SvoOrderResponse> rows,int stage){
        var avg=rows.stream().map(o->stage==0?o.requestToCollectionMinutes():stage==1?o.collectionToDeliveryMinutes():o.requestToDeliveryMinutes())
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();return avg.isPresent()?avg.getAsDouble():null;
    }
}
