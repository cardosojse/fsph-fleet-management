package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.CancelSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreateSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.requests.TriageSvoDeathRequest;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.SvoRequestStatus;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.SvoDeathRequest;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SvoDeathRequestRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class SvoDeathRequestService {

    private final SvoDeathRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public SvoDeathRequestService(
            SvoDeathRequestRepository requestRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SvoDeathRequest create(CreateSvoDeathRequest request, AuthenticatedUser actor) {
        if (request == null) {
            throw new InvalidRequestException("Dados da solicitação não informados");
        }
        if (actor == null || actor.id() == null) {
            throw new InvalidRequestException("Usuário solicitante autenticado é obrigatório");
        }

        User requester = userRepository.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", actor.id()));

        String protocolNumber = generateProtocolNumber();

        SvoDeathRequest deathRequest = new SvoDeathRequest(
                protocolNumber,
                requester,
                request.deceasedName(),
                request.deceasedAge(),
                request.deceasedGender(),
                request.occurredAt(),
                request.deathLocationType(),
                request.address(),
                request.neighborhood(),
                request.city(),
                request.referencePoint(),
                request.contactName(),
                request.contactPhone(),
                request.relationshipWithDeceased(),
                request.presumedCircumstance(),
                request.hasSuspectedViolence(),
                request.notes()
        );

        SvoDeathRequest saved = requestRepository.save(deathRequest);
        auditLogService.record(
                actor,
                "SVO_REQUEST_CREATED",
                "SVO_REQUEST",
                saved.getId().toString(),
                "Registrou solicitação SVO protocolo " + saved.getProtocolNumber() +
                        " para ocorrência em " + saved.getAddress() + ", " + saved.getNeighborhood()
        );
        return saved;
    }

    @Transactional
    public SvoDeathRequest cancel(UUID id, CancelSvoDeathRequest cancelReq, AuthenticatedUser actor) {
        SvoDeathRequest deathRequest = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação SVO", id));

        boolean canManage = actor.permissions() != null &&
                (actor.permissions().contains(Permission.SVO_REQUESTS_MANAGE) ||
                 actor.permissions().contains(Permission.USERS_WRITE));

        if (!deathRequest.getRequester().getId().equals(actor.id()) && !canManage) {
            throw new InvalidRequestException("Você não tem permissão para cancelar uma solicitação aberta por outro usuário");
        }

        String reason = cancelReq != null ? cancelReq.reason() : null;
        deathRequest.cancel(reason);
        SvoDeathRequest saved = requestRepository.save(deathRequest);

        auditLogService.record(
                actor,
                "SVO_REQUEST_CANCELED",
                "SVO_REQUEST",
                saved.getId().toString(),
                "Cancelou a solicitação SVO protocolo " + saved.getProtocolNumber()
        );
        return saved;
    }

    @Transactional
    public SvoDeathRequest triage(UUID id, TriageSvoDeathRequest triageReq, AuthenticatedUser actor) {
        if (triageReq == null) {
            throw new InvalidRequestException("Dados da triagem não informados");
        }

        SvoDeathRequest deathRequest = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação SVO", id));

        User triagedBy = userRepository.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", actor.id()));

        deathRequest.triage(triagedBy, Boolean.TRUE.equals(triageReq.approved()), triageReq.rejectionReason());
        SvoDeathRequest saved = requestRepository.save(deathRequest);

        String outcome = saved.getStatus() == SvoRequestStatus.TRIAGED ? "APROVADA" : "RECUSADA";
        auditLogService.record(
                actor,
                "SVO_REQUEST_TRIAGED",
                "SVO_REQUEST",
                saved.getId().toString(),
                "Realizou a triagem da solicitação SVO protocolo " + saved.getProtocolNumber() + " -> " + outcome
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<SvoDeathRequest> listMyRequests(AuthenticatedUser actor, Pageable pageable) {
        if (actor == null || actor.id() == null) {
            throw new InvalidRequestException("Usuário autenticado é obrigatório");
        }
        return requestRepository.findByRequesterIdOrderByCreatedAtDesc(actor.id(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<SvoDeathRequest> listAll(
            SvoRequestStatus status,
            String city,
            Instant from,
            Instant to,
            String search,
            Pageable pageable
    ) {
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String normalizedCity = (city != null && !city.isBlank()) ? city.trim() : null;
        return requestRepository.findFiltered(status, normalizedCity, from, to, normalizedSearch, pageable);
    }

    @Transactional(readOnly = true)
    public SvoDeathRequest findById(UUID id, AuthenticatedUser actor) {
        SvoDeathRequest deathRequest = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação SVO", id));

        boolean canReadAll = actor != null && actor.permissions() != null &&
                (actor.permissions().contains(Permission.SVO_REQUESTS_READ) ||
                 actor.permissions().contains(Permission.SVO_REQUESTS_MANAGE));

        if (!canReadAll && !deathRequest.getRequester().getId().equals(actor.id())) {
            throw new ResourceNotFoundException("Solicitação SVO", id);
        }
        return deathRequest;
    }

    @Transactional(readOnly = true)
    public SvoDeathRequest findByProtocolNumber(String protocolNumber, AuthenticatedUser actor) {
        if (protocolNumber == null || protocolNumber.isBlank()) {
            throw new InvalidRequestException("Número de protocolo é obrigatório");
        }
        SvoDeathRequest deathRequest = requestRepository.findByProtocolNumberIgnoreCase(protocolNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação SVO com protocolo " + protocolNumber.trim(), protocolNumber.trim()));

        boolean canReadAll = actor != null && actor.permissions() != null &&
                (actor.permissions().contains(Permission.SVO_REQUESTS_READ) ||
                 actor.permissions().contains(Permission.SVO_REQUESTS_MANAGE));

        if (!canReadAll && !deathRequest.getRequester().getId().equals(actor.id())) {
            throw new ResourceNotFoundException("Solicitação SVO", protocolNumber.trim());
        }
        return deathRequest;
    }

    private synchronized String generateProtocolNumber() {
        LocalDate today = LocalDate.now();
        Instant startOfDay = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long countToday = requestRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        String datePrefix = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequential = String.format("%04d", countToday + 1);
        String candidate = "SVO-" + datePrefix + "-" + sequential;

        int extra = 1;
        while (requestRepository.existsByProtocolNumberIgnoreCase(candidate)) {
            candidate = "SVO-" + datePrefix + "-" + String.format("%04d", countToday + 1 + extra++);
        }
        return candidate;
    }
}
