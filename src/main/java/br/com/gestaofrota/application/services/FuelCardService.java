package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.CreateFuelCardRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateFuelCardRequest;
import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.FuelCard;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.FuelCardRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class FuelCardService {

    private final FuelCardRepository fuelCardRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditLogService auditLogService;

    public FuelCardService(
            FuelCardRepository fuelCardRepository,
            VehicleRepository vehicleRepository,
            AuditLogService auditLogService
    ) {
        this.fuelCardRepository = fuelCardRepository;
        this.vehicleRepository = vehicleRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public FuelCard create(CreateFuelCardRequest request, AuthenticatedUser actor) {
        if (request == null) {
            throw new InvalidRequestException("Dados do cartão não informados");
        }
        String normalizedCardNumber = request.cardNumber().trim();
        if (fuelCardRepository.existsByCardNumberIgnoreCase(normalizedCardNumber)) {
            throw new ConflictException("Já existe um cartão de abastecimento com este número");
        }

        FuelCard card = new FuelCard(
                normalizedCardNumber,
                request.cardholderName(),
                request.totalLimit(),
                request.availableBalance(),
                request.expirationDate(),
                request.notes()
        );

        if (request.vehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Veículo", request.vehicleId()));
            if (!vehicle.isActive()) {
                throw new InvalidRequestException("Não é possível vincular cartão a um veículo inativo");
            }
            Optional<FuelCard> existing = fuelCardRepository.findByVehicleId(request.vehicleId());
            if (existing.isPresent()) {
                throw new ConflictException("O veículo informado já está vinculado ao cartão " + existing.get().getCardNumber());
            }
            card.bindVehicle(vehicle);
        }

        FuelCard saved = fuelCardRepository.save(card);
        auditLogService.record(
                actor,
                "FUEL_CARD_CREATED",
                "FUEL_CARD",
                saved.getId().toString(),
                "Cadastrou cartão de abastecimento " + saved.getCardNumber() +
                        (saved.getVehicle() != null ? " vinculado ao veículo placa " + saved.getVehicle().getPlate() : "")
        );
        return saved;
    }

    @Transactional
    public FuelCard update(UUID id, UpdateFuelCardRequest request, AuthenticatedUser actor) {
        FuelCard card = fuelCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível", id));

        card.update(
                request.cardholderName(),
                request.totalLimit(),
                request.availableBalance(),
                request.expirationDate(),
                request.notes()
        );

        FuelCard saved = fuelCardRepository.save(card);
        auditLogService.record(
                actor,
                "FUEL_CARD_UPDATED",
                "FUEL_CARD",
                saved.getId().toString(),
                "Atualizou os dados do cartão de abastecimento " + saved.getCardNumber()
        );
        return saved;
    }

    @Transactional
    public FuelCard updateStatus(UUID id, FuelCardStatus status, AuthenticatedUser actor) {
        FuelCard card = fuelCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível", id));

        card.updateStatus(status);
        FuelCard saved = fuelCardRepository.save(card);
        auditLogService.record(
                actor,
                "FUEL_CARD_STATUS_UPDATED",
                "FUEL_CARD",
                saved.getId().toString(),
                "Alterou o status do cartão " + saved.getCardNumber() + " para " + status
        );
        return saved;
    }

    @Transactional
    public FuelCard bindVehicle(UUID id, UUID vehicleId, AuthenticatedUser actor) {
        FuelCard card = fuelCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível", id));

        if (card.getStatus() == FuelCardStatus.CANCELED) {
            throw new InvalidRequestException("Não é possível vincular um veículo a um cartão cancelado");
        }

        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", vehicleId));

        if (!vehicle.isActive()) {
            throw new InvalidRequestException("Não é possível vincular cartão a um veículo inativo");
        }

        Optional<FuelCard> existing = fuelCardRepository.findByVehicleId(vehicleId);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new ConflictException("O veículo informado já está vinculado ao cartão " + existing.get().getCardNumber());
        }

        card.bindVehicle(vehicle);
        FuelCard saved = fuelCardRepository.save(card);
        auditLogService.record(
                actor,
                "FUEL_CARD_VEHICLE_BOUND",
                "FUEL_CARD",
                saved.getId().toString(),
                "Vinculou o cartão " + saved.getCardNumber() + " ao veículo placa " + vehicle.getPlate()
        );
        return saved;
    }

    @Transactional
    public FuelCard unbindVehicle(UUID id, AuthenticatedUser actor) {
        FuelCard card = fuelCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível", id));

        String previousPlate = card.getVehicle() != null ? card.getVehicle().getPlate() : null;
        card.unbindVehicle();
        FuelCard saved = fuelCardRepository.save(card);
        auditLogService.record(
                actor,
                "FUEL_CARD_VEHICLE_UNBOUND",
                "FUEL_CARD",
                saved.getId().toString(),
                "Desvinculou o cartão " + saved.getCardNumber() +
                        (previousPlate != null ? " do veículo placa " + previousPlate : "")
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public FuelCard findById(UUID id) {
        return fuelCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível", id));
    }

    @Transactional(readOnly = true)
    public FuelCard findByCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new InvalidRequestException("Número do cartão deve ser informado");
        }
        return fuelCardRepository.findByCardNumberIgnoreCase(cardNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível", cardNumber.trim()));
    }

    @Transactional(readOnly = true)
    public FuelCard findByVehicleId(UUID vehicleId) {
        return fuelCardRepository.findByVehicleId(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Cartão de combustível do veículo", vehicleId));
    }

    @Transactional(readOnly = true)
    public Page<FuelCard> list(FuelCardStatus status, UUID vehicleId, String search, Pageable pageable) {
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return fuelCardRepository.findFiltered(status, vehicleId, normalizedSearch, pageable);
    }
}
