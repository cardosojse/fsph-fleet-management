package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.CreateFuelCardRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateFuelCardRequest;
import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.FuelCard;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.FuelCardRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuelCardServiceTests {

    @Mock
    private FuelCardRepository fuelCardRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private AuditLogService auditLogService;

    private FuelCardService fuelCardService;

    private AuthenticatedUser actor;
    private Sector sector;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        fuelCardService = new FuelCardService(fuelCardRepository, vehicleRepository, auditLogService);
        actor = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Gestor Frota",
                "gestor@fsph.se.gov.br",
                "hash",
                UserRole.GESTOR_FROTA,
                true,
                Set.of(Permission.FUEL_CARDS_READ, Permission.FUEL_CARDS_WRITE)
        );
        sector = new Sector("Transportes");
        vehicle = new Vehicle(sector, "ABC1D23", "Gol 1.0", 5, "ADMINISTRATIVO", 50000L);
        ReflectionTestUtils.setField(vehicle, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Deve cadastrar cartão de abastecimento sem veículo vinculado com sucesso")
    void createFuelCardSuccessfullyWithoutVehicle() {
        CreateFuelCardRequest request = new CreateFuelCardRequest(
                "6034123456789012",
                "Cartão Prime Frota Reserva",
                LocalDate.now().plusYears(2),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                null,
                "Cartão de contingência"
        );

        when(fuelCardRepository.existsByCardNumberIgnoreCase("6034123456789012")).thenReturn(false);
        when(fuelCardRepository.save(any(FuelCard.class))).thenAnswer(invocation -> {
            FuelCard c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });

        FuelCard created = fuelCardService.create(request, actor);

        assertThat(created).isNotNull();
        assertThat(created.getCardNumber()).isEqualTo("6034123456789012");
        assertThat(created.getStatus()).isEqualTo(FuelCardStatus.ACTIVE);
        assertThat(created.getTotalLimit()).isEqualByComparingTo("5000.00");
        assertThat(created.getAvailableBalance()).isEqualByComparingTo("5000.00");
        assertThat(created.getVehicle()).isNull();
        verify(auditLogService).record(eq(actor), eq("FUEL_CARD_CREATED"), eq("FUEL_CARD"), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve cadastrar cartão já vinculado a um veículo ativo com sucesso")
    void createFuelCardSuccessfullyWithVehicle() {
        UUID vehicleId = vehicle.getId();
        CreateFuelCardRequest request = new CreateFuelCardRequest(
                "6034999988887777",
                "Cartão Gol ABC1D23",
                LocalDate.now().plusYears(1),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                vehicleId,
                "Cartão principal do Gol"
        );

        when(fuelCardRepository.existsByCardNumberIgnoreCase("6034999988887777")).thenReturn(false);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(fuelCardRepository.findByVehicleId(vehicleId)).thenReturn(Optional.empty());
        when(fuelCardRepository.save(any(FuelCard.class))).thenAnswer(invocation -> {
            FuelCard c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });

        FuelCard created = fuelCardService.create(request, actor);

        assertThat(created.getVehicle()).isEqualTo(vehicle);
        verify(auditLogService).record(eq(actor), eq("FUEL_CARD_CREATED"), eq("FUEL_CARD"), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve lançar ConflictException ao tentar cadastrar cartão com número duplicado")
    void throwWhenCardNumberAlreadyExists() {
        CreateFuelCardRequest request = new CreateFuelCardRequest(
                "6034111122223333",
                "Cartão Duplicado",
                LocalDate.now().plusYears(1),
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                null,
                null
        );

        when(fuelCardRepository.existsByCardNumberIgnoreCase("6034111122223333")).thenReturn(true);

        assertThatThrownBy(() -> fuelCardService.create(request, actor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Já existe um cartão de abastecimento com este número");
    }

    @Test
    @DisplayName("Deve lançar ConflictException se o veículo já estiver vinculado a outro cartão no cadastro")
    void throwWhenVehicleAlreadyBoundOnCreation() {
        UUID vehicleId = vehicle.getId();
        FuelCard existingCard = new FuelCard(
                "6034000000000001",
                "Cartão Existente",
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                LocalDate.now().plusYears(1),
                null
        );
        existingCard.bindVehicle(vehicle);

        CreateFuelCardRequest request = new CreateFuelCardRequest(
                "6034000000000002",
                "Novo Cartão",
                LocalDate.now().plusYears(1),
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                vehicleId,
                null
        );

        when(fuelCardRepository.existsByCardNumberIgnoreCase("6034000000000002")).thenReturn(false);
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(fuelCardRepository.findByVehicleId(vehicleId)).thenReturn(Optional.of(existingCard));

        assertThatThrownBy(() -> fuelCardService.create(request, actor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("O veículo informado já está vinculado ao cartão");
    }

    @Test
    @DisplayName("Deve atualizar dados cadastrais do cartão com sucesso")
    void updateFuelCardSuccessfully() {
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Nome Antigo",
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                LocalDate.now().plusMonths(6),
                "Obs antiga"
        );
        UUID cardId = UUID.randomUUID();
        ReflectionTestUtils.setField(card, "id", cardId);

        when(fuelCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(fuelCardRepository.save(card)).thenReturn(card);

        UpdateFuelCardRequest updateRequest = new UpdateFuelCardRequest(
                "Nome Atualizado",
                LocalDate.now().plusYears(2),
                new BigDecimal("4500.00"),
                new BigDecimal("4000.00"),
                "Nova observação"
        );

        FuelCard updated = fuelCardService.update(cardId, updateRequest, actor);

        assertThat(updated.getCardholderName()).isEqualTo("Nome Atualizado");
        assertThat(updated.getTotalLimit()).isEqualByComparingTo("4500.00");
        assertThat(updated.getAvailableBalance()).isEqualByComparingTo("4000.00");
        assertThat(updated.getNotes()).isEqualTo("Nova observação");
        verify(auditLogService).record(eq(actor), eq("FUEL_CARD_UPDATED"), eq("FUEL_CARD"), eq(cardId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve atualizar status do cartão com sucesso")
    void updateStatusSuccessfully() {
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Titular",
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                LocalDate.now().plusMonths(6),
                null
        );
        UUID cardId = UUID.randomUUID();
        ReflectionTestUtils.setField(card, "id", cardId);

        when(fuelCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(fuelCardRepository.save(card)).thenReturn(card);

        FuelCard updated = fuelCardService.updateStatus(cardId, FuelCardStatus.BLOCKED, actor);

        assertThat(updated.getStatus()).isEqualTo(FuelCardStatus.BLOCKED);
        verify(auditLogService).record(eq(actor), eq("FUEL_CARD_STATUS_UPDATED"), eq("FUEL_CARD"), eq(cardId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve vincular veículo ao cartão com sucesso")
    void bindVehicleSuccessfully() {
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Cartão Reserva",
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                LocalDate.now().plusYears(1),
                null
        );
        UUID cardId = UUID.randomUUID();
        ReflectionTestUtils.setField(card, "id", cardId);
        UUID vehicleId = vehicle.getId();

        when(fuelCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(fuelCardRepository.findByVehicleId(vehicleId)).thenReturn(Optional.empty());
        when(fuelCardRepository.save(card)).thenReturn(card);

        FuelCard bound = fuelCardService.bindVehicle(cardId, vehicleId, actor);

        assertThat(bound.getVehicle()).isEqualTo(vehicle);
        verify(auditLogService).record(eq(actor), eq("FUEL_CARD_VEHICLE_BOUND"), eq("FUEL_CARD"), eq(cardId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve desvincular veículo do cartão com sucesso")
    void unbindVehicleSuccessfully() {
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Cartão Gol",
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00"),
                LocalDate.now().plusYears(1),
                null
        );
        card.bindVehicle(vehicle);
        UUID cardId = UUID.randomUUID();
        ReflectionTestUtils.setField(card, "id", cardId);

        when(fuelCardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(fuelCardRepository.save(card)).thenReturn(card);

        FuelCard unbound = fuelCardService.unbindVehicle(cardId, actor);

        assertThat(unbound.getVehicle()).isNull();
        verify(auditLogService).record(eq(actor), eq("FUEL_CARD_VEHICLE_UNBOUND"), eq("FUEL_CARD"), eq(cardId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve permitir débito e crédito de saldo e impedir débito superior ao saldo disponível")
    void debitAndCreditBalance() {
        FuelCard card = new FuelCard(
                "6034123456789012",
                "Cartão Teste",
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                LocalDate.now().plusYears(1),
                null
        );

        card.debit(new BigDecimal("200.00"));
        assertThat(card.getAvailableBalance()).isEqualByComparingTo("300.00");

        card.credit(new BigDecimal("150.00"));
        assertThat(card.getAvailableBalance()).isEqualByComparingTo("450.00");

        assertThatThrownBy(() -> card.debit(new BigDecimal("500.00")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Saldo insuficiente");
    }
}
