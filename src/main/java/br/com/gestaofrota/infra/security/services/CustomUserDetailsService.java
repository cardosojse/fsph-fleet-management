package br.com.gestaofrota.infra.security.services;

import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.application.services.RolePermissionService;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    public static final String STAGING_DEMO_EMAIL = "demo@frota.test";

    private final UserRepository userRepository;
    private final RolePermissionService rolePermissionService;
    private final Environment environment;

    public CustomUserDetailsService(UserRepository userRepository, RolePermissionService rolePermissionService, Environment environment) {
        this.userRepository = userRepository;
        this.rolePermissionService = rolePermissionService;
        this.environment = environment;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (STAGING_DEMO_EMAIL.equals(normalizedEmail)
                && (!environment.acceptsProfiles(Profiles.of("staging & !prod & !production"))
                || !"staging".equals(environment.getProperty("app.environment", "production"))
                || !environment.getProperty("app.staging.demo.enabled", Boolean.class, false))) {
            throw new UsernameNotFoundException("Credenciais inválidas");
        }

        User user = userRepository.findByEmailIgnoreCaseAndActiveTrue(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

        return new AuthenticatedUser(
                user.getId(),
                user.getSector().getId(),
                user.getName(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.isActive(),
                rolePermissionService.find(user.getRole())
        );
    }
}
