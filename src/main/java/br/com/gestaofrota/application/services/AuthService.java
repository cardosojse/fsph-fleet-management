package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.LoginResult;
import br.com.gestaofrota.infra.security.jwt.JwtService;
import br.com.gestaofrota.infra.security.services.RevokedTokenService;
import br.com.gestaofrota.domain.exceptions.InvalidTokenException;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RevokedTokenService revokedTokenService;
    private final AuditLogService auditLogService;

    @Autowired
    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RevokedTokenService revokedTokenService,
            @Autowired(required = false) AuditLogService auditLogService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.revokedTokenService = revokedTokenService;
        this.auditLogService = auditLogService;
    }

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService, RevokedTokenService revokedTokenService) {
        this(authenticationManager, jwtService, revokedTokenService, null);
    }

    public LoginResult login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        normalizedEmail,
                        password
                )
        );

        UserDetails user = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(user);

        if (auditLogService != null) {
            UUID userId = user instanceof AuthenticatedUser authUser ? authUser.id() : null;
            String roleName = user instanceof AuthenticatedUser authUser && authUser.role() != null ? authUser.role().name() : null;
            String name = user instanceof AuthenticatedUser authUser ? authUser.name() : user.getUsername();
            auditLogService.record(
                    userId,
                    name,
                    user.getUsername(),
                    roleName,
                    "USER_LOGIN",
                    "AUTH",
                    userId != null ? userId.toString() : user.getUsername(),
                    "Login realizado com sucesso",
                    null
            );
        }

        return new LoginResult(accessToken, jwtService.getExpirationSeconds());
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token ausente ou inválido");
        }

        try {
            revokedTokenService.revoke(token);
            if (auditLogService != null) {
                auditLogService.recordCurrent("USER_LOGOUT", "AUTH", null, "Logout realizado com sucesso");
            }
        } catch (JWTVerificationException exception) {
            throw new InvalidTokenException("Token inválido ou expirado", exception);
        }
    }
}
