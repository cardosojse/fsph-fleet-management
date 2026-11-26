package br.com.gestaofrota.api.v1.controllers;
import br.com.gestaofrota.api.v1.dtos.requests.*;
import br.com.gestaofrota.api.v1.dtos.responses.*;
import br.com.gestaofrota.application.services.SvoCollectionOrderService;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/svo")
public class SvoCollectionOrderController {
    private final SvoCollectionOrderService service;
    public SvoCollectionOrderController(SvoCollectionOrderService service){this.service=service;}
    @PostMapping("/requests/{id}/order") @PreAuthorize("hasAuthority('SVO_REQUESTS_MANAGE') and hasAuthority('SVO_ORDERS_READ')")
    public SvoOrderResponse generate(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable UUID id){return service.generate(a,id);}
    @GetMapping("/orders") @PreAuthorize("hasAuthority('SVO_ORDERS_READ')")
    public PageResponse<SvoOrderResponse> list(@AuthenticationPrincipal AuthenticatedUser a,
            @RequestParam(required=false) SvoOrderStatus status,@RequestParam(required=false) String search,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required=false) UUID teamId,@RequestParam(required=false) UUID vehicleId,@RequestParam(required=false) UUID requestId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(a,status,search,from,to,teamId,vehicleId,requestId,page,size);}
    @GetMapping("/orders/report") @PreAuthorize("hasAuthority('SVO_ORDERS_REPORT')")
    public SvoOrderReportResponse report(@AuthenticationPrincipal AuthenticatedUser a,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required=false) UUID teamId,@RequestParam(required=false) UUID vehicleId){return service.report(a,from,to,teamId,vehicleId);}
    @GetMapping("/orders/{id}") @PreAuthorize("hasAuthority('SVO_ORDERS_READ')")
    public SvoOrderResponse find(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable UUID id){return service.find(a,id);}
    @GetMapping("/orders/{id}/resources") @PreAuthorize("hasAuthority('SVO_ORDERS_MANAGE')")
    public SvoOrderResourcesResponse resources(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable UUID id){return service.resources(a,id);}
    @PutMapping("/orders/{id}/assignment") @PreAuthorize("hasAuthority('SVO_ORDERS_MANAGE')")
    public SvoOrderResponse assign(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable UUID id,@RequestBody @Valid AssignSvoOrderRequest r){return service.assign(a,id,r);}
    @PatchMapping("/orders/{id}/status") @PreAuthorize("hasAnyAuthority('SVO_ORDERS_EXECUTE','SVO_ORDERS_MANAGE')")
    public SvoOrderResponse transition(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable UUID id,@RequestBody @Valid TransitionSvoOrderRequest r){return service.transition(a,id,r);}
    @PostMapping("/orders/{id}/notes") @PreAuthorize("hasAuthority('SVO_ORDERS_NOTES')")
    public SvoOrderResponse note(@AuthenticationPrincipal AuthenticatedUser a,@PathVariable UUID id,@RequestBody @Valid AddSvoOrderNoteRequest r){return service.note(a,id,r);}
}
