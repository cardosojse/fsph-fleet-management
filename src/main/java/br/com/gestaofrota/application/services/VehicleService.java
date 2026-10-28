package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.AssignmentProviderUnavailableException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.DriverRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Optional;

@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final SectorRepository sectors;
    private final DriverRepository drivers;
    private final Optional<VehicleAssignmentQuery> assignments;

    public VehicleService(VehicleRepository vehicles, SectorRepository sectors, DriverRepository drivers,
                          Optional<VehicleAssignmentQuery> assignments) {
        this.vehicles = vehicles;
        this.sectors = sectors;
        this.drivers = drivers;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> list() { return vehicles.findAll(Sort.by("plate")); }

    @Transactional(readOnly = true)
    public Vehicle find(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Veículo", id));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> assignedToUser(UUID authenticatedUserId) {
        var driver = drivers.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Motorista", authenticatedUserId));
        if (!driver.isActive()) {
            throw new ConflictException("Motorista inativo não pode consultar veículos vinculados");
        }
        VehicleAssignmentQuery query = assignments.orElseThrow(AssignmentProviderUnavailableException::new);
        List<UUID> assignedVehicleIds = query.findActiveVehiclesForDriver(driver.getId()).stream()
                .filter(Vehicle::isActive)
                .map(Vehicle::getId)
                .toList();
        if (assignedVehicleIds.isEmpty()) return List.of();
        return vehicles.findAllWithSectorByIdIn(assignedVehicleIds).stream().filter(Vehicle::isActive).toList();
    }

    @Transactional
    public Vehicle create(UUID sectorId, String plate, String model, int capacity, String usageType, long odometerKm) {
        String normalizedPlate = normalizePlate(plate);

        if (vehicles.existsByPlateIgnoreCase(normalizedPlate)) {
            throw new ConflictException("Já existe um veículo com esta placa");
        }

        return vehicles.save(new Vehicle(
                findSector(sectorId),
                normalizedPlate,
                model.trim(),
                capacity,
                usageType.trim(),
                odometerKm
            )
        );
    }

    @Transactional
    public Vehicle update(UUID id, UUID sectorId, String plate, String model, int capacity, String usageType, long odometerKm) {
        Vehicle vehicle = find(id);

        if (!vehicle.isActive()) {
            throw new ConflictException("Veículo inativo não pode ser alterado");
        }

        if (odometerKm < vehicle.getCurrentOdometerKm()) {
            throw new ConflictException("A quilometragem não pode ser menor que a atual");
        }

        String normalizedPlate = normalizePlate(plate);

        if (vehicles.existsByPlateIgnoreCaseAndIdNot(normalizedPlate, id)) {
            throw new ConflictException("Já existe um veículo com esta placa");
        }

        vehicle.update(findSector(sectorId), normalizedPlate, model.trim(), capacity, usageType.trim(), odometerKm);
        return vehicle;
    }

    @Transactional
    public void deactivate(UUID id) {
        Vehicle vehicle = find(id);
        vehicle.deactivate();
    }

    private Sector findSector(UUID id) {
        if (id == null) return null;

        return sectors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Setor", id));
    }

    private String normalizePlate(String plate) {
        String normalized = plate.trim().replace("-", "").toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z0-9]{7,10}")) {
            throw new InvalidRequestException("Placa deve conter de 7 a 10 letras ou números");

        }
        return normalized;
    }
}
