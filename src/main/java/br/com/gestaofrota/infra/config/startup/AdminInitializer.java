package br.com.gestaofrota.infra.config.startup;

import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.domain.enums.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.text.Normalizer;
import java.util.Set;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties properties;

    public AdminInitializer(UserRepository userRepository, SectorRepository sectorRepository, PasswordEncoder passwordEncoder, AdminProperties properties) {
        this.userRepository = userRepository;
        this.sectorRepository = sectorRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedEmail = properties.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            return;
        }

        String sectorName = properties.sectorName().trim();
        Sector administrativeSector = sectorRepository.findByNameIgnoreCase(sectorName)
                .orElseGet(() -> findOrCreateSector(sectorName));

        User administrator = new User(
            administrativeSector,
            properties.name().trim(),
            normalizedEmail,
            passwordEncoder.encode(properties.password()),
            UserRole.ADMIN
        );

        userRepository.save(administrator);
    }

    private Sector findOrCreateSector(String sectorName) {
        // Keep the original ID when the bootstrap unit uses an existing administrative alias.
        if (isAdministrative(sectorName)) {
            return sectorRepository.findAll().stream()
                    .filter(sector -> isAdministrative(sector.getName()))
                    .findFirst()
                    .orElseGet(() -> sectorRepository.save(new Sector(sectorName)));
        }
        return sectorRepository.save(new Sector(sectorName));
    }

    private boolean isAdministrative(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return Set.of("administrativo", "administracao", "setor administrativo").contains(normalized);
    }
}
