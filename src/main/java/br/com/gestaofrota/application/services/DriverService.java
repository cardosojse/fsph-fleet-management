
package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.DriverRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DriverService {

    private final DriverRepository drivers;
    private final UserRepository users;

    public DriverService(DriverRepository drivers, UserRepository users) {
        this.drivers = drivers;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Driver> list() {
        return drivers.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Driver find(UUID id) {
        return drivers.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Motorista", id));
    }

    @Transactional
    public Driver create(UUID userId) {

        User user = users.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Usuário", userId));

        if (!user.isActive() || user.getRole() != UserRole.MOTORISTA) {
            throw new ConflictException(
                    "O usuário deve estar ativo e possuir perfil MOTORISTA"
            );
        }

        if (drivers.existsByUserId(userId)) {
            throw new ConflictException(
                    "Este usuário já possui cadastro de motorista"
            );
        }

        return drivers.save(new Driver(user));
    }

    @Transactional
    public void deactivate(UUID id) {
        Driver driver = find(id);
        driver.deactivate();
    }

    @Transactional(readOnly = true)
    public List<Driver> findByActive(boolean active) {
        return drivers.findByActive(active);
    }

    @Transactional(readOnly = true)
    public List<Driver> findByRole(UserRole role) {
        return drivers.findByUser_Role(role);
    }

    @Transactional(readOnly = true)
    public List<Driver> findBySector(Sector sector) {
        return drivers.findByUser_Sector(sector);
    }

}
