package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.responses.AuditLogPageResponse;
import br.com.gestaofrota.application.services.AuditLogService;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_LOGS_READ')")
    public AuditLogPageResponse list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new InvalidRequestException("O número da página não pode ser negativo");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("O tamanho da página deve estar entre 1 e 100");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new InvalidRequestException("A data final não pode ser anterior à data inicial");
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return AuditLogPageResponse.from(
                auditLogService.listLogs(from, to, actorUserId, action, resourceType, pageable)
        );
    }
}
