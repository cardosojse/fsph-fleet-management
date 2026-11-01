package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

public record RouteSchedule(Instant plannedStartAt, int plannedDurationMinutes, Instant plannedReturnAt) {
    public RouteSchedule {
        if (plannedStartAt == null || plannedReturnAt == null || plannedDurationMinutes <= 0) {
            throw new InvalidRequestException("Informe início e duração positiva para o planejamento");
        }
        if (!returnAt(plannedStartAt, plannedDurationMinutes).equals(plannedReturnAt)) {
            throw new InvalidRequestException("O retorno previsto deve corresponder ao início mais a duração em minutos");
        }
    }

    public static RouteSchedule resolve(Instant start, Integer minutes, Instant returnAt) {
        if (start == null || (minutes == null && returnAt == null)) {
            throw new InvalidRequestException("Informe início e duração em minutos ou retorno previsto");
        }
        if (minutes == null) {
            Duration duration = Duration.between(start, returnAt);
            if (duration.isNegative() || duration.isZero() || duration.getNano() != 0
                    || duration.getSeconds() % 60 != 0 || duration.toMinutes() > Integer.MAX_VALUE) {
                throw new InvalidRequestException("O retorno deve ser posterior ao início por um número inteiro de minutos");
            }
            minutes = (int) duration.toMinutes();
        }
        return new RouteSchedule(start, minutes, returnAt == null ? returnAt(start, minutes) : returnAt);
    }

    private static Instant returnAt(Instant start, int minutes) {
        try {
            return start.plusSeconds(minutes * 60L);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new InvalidRequestException("O planejamento excede o intervalo de datas permitido");
        }
    }
}
