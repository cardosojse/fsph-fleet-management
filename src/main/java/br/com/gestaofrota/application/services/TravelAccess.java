package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.UnauthorizedAccessException;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

final class TravelAccess {
    private TravelAccess() {}

    static boolean has(AuthenticatedUser actor, Permission permission) {
        return actor != null && actor.active()
                && (actor.role() == UserRole.ADMIN || actor.permissions().contains(permission));
    }

    static void require(AuthenticatedUser actor, Permission permission) {
        if (!has(actor, permission)) throw new UnauthorizedAccessException();
    }

    static void requireRead(AuthenticatedUser actor, Permission read, Permission manage) {
        if (!has(actor, read) && !has(actor, manage)) throw new UnauthorizedAccessException();
    }

    static PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Página deve ser não negativa e tamanho deve estar entre 1 e 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }
}
