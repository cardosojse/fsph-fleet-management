package br.com.gestaofrota.infra.config.startup;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.services.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Component
@Profile("staging & !prod & !production")
@ConditionalOnProperty(name = "app.staging.demo.enabled", havingValue = "true")
public class StagingDemoInitializer implements ApplicationRunner {

    static final String EMAIL = CustomUserDetailsService.STAGING_DEMO_EMAIL;
    static final String NAME = "Demonstracao de Homologacao";
    static final String SECTOR = "Demonstracao de Homologacao";

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final PasswordEncoder passwordEncoder;
    private final String environment;
    private final String password;

    public StagingDemoInitializer(
            UserRepository userRepository,
            SectorRepository sectorRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.environment:production}") String environment,
            @Value("${app.staging.demo.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.sectorRepository = sectorRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!"staging".equals(environment)) {
            throw new IllegalStateException("Demo initialization requires APP_ENVIRONMENT=staging");
        }
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("STAGING_DEMO_PASSWORD requires at least 12 characters and at most 72 UTF-8 bytes");
        }

        User existing = userRepository.findByEmailIgnoreCase(EMAIL).orElse(null);
        if (existing != null) {
            if (!NAME.equals(existing.getName()) || !SECTOR.equals(existing.getSector().getName())
                    || existing.getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("Demo email is already assigned to an unmanaged account");
            }
            boolean changed = false;
            if (!passwordEncoder.matches(password, existing.getPassword())) {
                existing.changePassword(passwordEncoder.encode(password));
                changed = true;
            }
            if (!existing.isActive()) {
                existing.reactivate();
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
            }
            return;
        }

        Sector sector = sectorRepository.findByNameIgnoreCase(SECTOR)
                .orElseGet(() -> sectorRepository.save(new Sector(SECTOR)));
        userRepository.save(new User(sector, NAME, EMAIL, passwordEncoder.encode(password), UserRole.ADMIN));
    }
}
