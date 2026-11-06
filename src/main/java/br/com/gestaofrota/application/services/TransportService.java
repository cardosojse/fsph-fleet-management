package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.CreateTransportRequest;
import br.com.gestaofrota.api.v1.dtos.responses.PageResponse;
import br.com.gestaofrota.api.v1.dtos.responses.TransportResponse;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.TransportStatus;
import br.com.gestaofrota.domain.exceptions.*;
import br.com.gestaofrota.domain.models.Transport;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.TransportRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransportService {
    private final TransportRepository transports;
    private final UserRepository users;

    public TransportService(TransportRepository transports, UserRepository users) {
        this.transports = transports;
        this.users = users;
    }

    @Transactional
    public TransportResponse create(AuthenticatedUser actor, CreateTransportRequest request) {
        TravelAccess.require(actor, Permission.TRANSPORTS_REQUEST);
        User requester = users.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", actor.id()));
        if (!requester.isActive()) throw new ConflictException("Solicitante inativo");
        return TransportResponse.from(transports.saveAndFlush(new Transport(requester, request.occurredAt(),
                request.serviceType(), request.pickupLocation(), request.destination())));
    }

    public PageResponse<TransportResponse> list(AuthenticatedUser actor, TransportStatus status,
                                                UUID sectorId, int page, int size) {
        TravelAccess.requireRead(actor, Permission.TRANSPORTS_READ, Permission.TRANSPORTS_MANAGE);
        Specification<Transport> spec = (root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            if (!TravelAccess.has(actor, Permission.TRANSPORTS_MANAGE)) {
                filters.add(cb.equal(root.get("requestedByUser").get("id"), actor.id()));
            }
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (sectorId != null) filters.add(cb.equal(root.get("sector").get("id"), sectorId));
            return cb.and(filters.toArray(Predicate[]::new));
        };
        return PageResponse.from(transports.findAll(spec, TravelAccess.page(page, size)).map(TransportResponse::from));
    }

    public TransportResponse find(AuthenticatedUser actor, UUID id) {
        TravelAccess.requireRead(actor, Permission.TRANSPORTS_READ, Permission.TRANSPORTS_MANAGE);
        Transport transport = transports.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transporte", id));
        requireOwnerOrManager(actor, transport);
        return TransportResponse.from(transport);
    }

    @Transactional
    public TransportResponse cancel(AuthenticatedUser actor, UUID id) {
        if (!TravelAccess.has(actor, Permission.TRANSPORTS_REQUEST)
                && !TravelAccess.has(actor, Permission.TRANSPORTS_MANAGE)) throw new UnauthorizedAccessException();
        Transport transport = transports.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transporte", id));
        requireOwnerOrManager(actor, transport);
        transport.cancel();
        transports.flush();
        return TransportResponse.from(transport);
    }

    private void requireOwnerOrManager(AuthenticatedUser actor, Transport transport) {
        if (!TravelAccess.has(actor, Permission.TRANSPORTS_MANAGE)
                && !transport.getRequestedByUser().getId().equals(actor.id())) throw new UnauthorizedAccessException();
    }
}
