package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.FuelType;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/** Minimal report row; no user, sector, station document or access policy is inferred. */
public record FuelingReportItemResponse(
        UUID id, LocalDate fueledOn, UUID vehicleId, String vehiclePlate,
        String vehicleModel, FuelType fuelType, BigDecimal liters, BigDecimal totalValue
) {
    public FuelingReportItemResponse {
        if (id == null || fueledOn == null || vehicleId == null || fuelType == null
                || vehiclePlate == null || vehiclePlate.isBlank()
                || vehicleModel == null || vehicleModel.isBlank()
                || liters == null || liters.signum() <= 0
                || totalValue == null || totalValue.signum() <= 0) {
            throw new InvalidRequestException("Registro de abastecimento incompleto ou com litros/valor não positivos");
        }
        try {
            // Match the existing database precision without silently rounding input data.
            if (liters.compareTo(new BigDecimal("9999999.999")) > 0
                    || totalValue.compareTo(new BigDecimal("9999999999.99")) > 0) {
                throw new ArithmeticException("Precisão excedida");
            }
            liters = liters.stripTrailingZeros();
            totalValue = totalValue.stripTrailingZeros();
            if (liters.scale() > 3 || totalValue.scale() > 2) {
                throw new ArithmeticException("Escala excedida");
            }
            liters = liters.setScale(3, RoundingMode.UNNECESSARY);
            totalValue = totalValue.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidRequestException("Litros ou valor excedem a precisão dos registros de abastecimento");
        }
    }

    // JavaBean accessor also exposes this derived value in the future JSON response.
    public BigDecimal getPricePerLiter() {
        return totalValue.divide(liters, 2, RoundingMode.HALF_UP);
    }
}
