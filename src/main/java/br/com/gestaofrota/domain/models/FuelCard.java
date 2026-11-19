package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fuel_cards")
public class FuelCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_number", nullable = false, length = 50, unique = true)
    private String cardNumber;

    @Column(name = "cardholder_name", nullable = false, length = 120)
    private String cardholderName;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", unique = true)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FuelCardStatus status = FuelCardStatus.ACTIVE;

    @Column(name = "total_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLimit;

    @Column(name = "available_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FuelCard() {}

    public FuelCard(
            String cardNumber,
            String cardholderName,
            BigDecimal totalLimit,
            BigDecimal availableBalance,
            LocalDate expirationDate,
            String notes
    ) {
        setCardNumber(cardNumber);
        setCardholderName(cardholderName);
        setTotalLimit(totalLimit);
        setAvailableBalance(availableBalance != null ? availableBalance : totalLimit);
        setExpirationDate(expirationDate);
        this.notes = notes != null ? notes.trim() : null;
        this.status = FuelCardStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            throw new InvalidRequestException("Número do cartão é obrigatório");
        }
        this.cardNumber = cardNumber.trim();
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public void setCardholderName(String cardholderName) {
        if (cardholderName == null || cardholderName.isBlank()) {
            throw new InvalidRequestException("Nome/titular do cartão é obrigatório");
        }
        this.cardholderName = cardholderName.trim();
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public FuelCardStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalLimit() {
        return totalLimit;
    }

    public void setTotalLimit(BigDecimal totalLimit) {
        if (totalLimit == null || totalLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Limite total do cartão deve ser maior ou igual a zero");
        }
        this.totalLimit = totalLimit;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        if (availableBalance == null || availableBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Saldo disponível deve ser maior ou igual a zero");
        }
        this.availableBalance = availableBalance;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        if (expirationDate == null) {
            throw new InvalidRequestException("Data de validade do cartão é obrigatória");
        }
        this.expirationDate = expirationDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes.trim() : null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExpired() {
        return expirationDate != null && expirationDate.isBefore(LocalDate.now());
    }

    public boolean canTransact() {
        return status == FuelCardStatus.ACTIVE && !isExpired();
    }

    public void update(
            String cardholderName,
            BigDecimal totalLimit,
            BigDecimal availableBalance,
            LocalDate expirationDate,
            String notes
    ) {
        setCardholderName(cardholderName);
        setTotalLimit(totalLimit);
        setAvailableBalance(availableBalance);
        setExpirationDate(expirationDate);
        setNotes(notes);
    }

    public void updateStatus(FuelCardStatus status) {
        if (status == null) {
            throw new InvalidRequestException("Status do cartão é obrigatório");
        }
        this.status = status;
    }

    public void bindVehicle(Vehicle vehicle) {
        if (vehicle == null) {
            throw new InvalidRequestException("Veículo não pode ser nulo para vinculação");
        }
        this.vehicle = vehicle;
    }

    public void unbindVehicle() {
        this.vehicle = null;
    }

    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Valor do débito deve ser positivo");
        }
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new InvalidRequestException("Saldo insuficiente no cartão para realizar o abastecimento");
        }
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Valor do crédito deve ser positivo");
        }
        this.availableBalance = this.availableBalance.add(amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FuelCard that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
