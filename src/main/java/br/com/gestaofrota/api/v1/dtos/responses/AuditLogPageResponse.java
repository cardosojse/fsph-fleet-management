package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.AuditLog;
import org.springframework.data.domain.Page;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AuditLogPageResponse from(Page<AuditLog> page) {
        return new AuditLogPageResponse(
                page.getContent().stream().map(AuditLogResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
