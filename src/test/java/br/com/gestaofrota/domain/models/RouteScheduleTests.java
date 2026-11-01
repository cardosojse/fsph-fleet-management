package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class RouteScheduleTests {
    private final Instant start = Instant.parse("2026-11-02T11:00:00Z");

    @Test
    void acceptsDurationReturnOrBothAndProducesTheSameSchedule() {
        RouteSchedule byDuration = RouteSchedule.resolve(start, 90, null);
        RouteSchedule byReturn = RouteSchedule.resolve(start, null, start.plusSeconds(5400));
        RouteSchedule byBoth = RouteSchedule.resolve(start, 90, start.plusSeconds(5400));
        assertThat(byDuration).isEqualTo(byReturn).isEqualTo(byBoth);
        assertThat(byReturn.plannedDurationMinutes()).isEqualTo(90);
        assertThat(byDuration.plannedReturnAt()).isEqualTo(Instant.parse("2026-11-02T12:30:00Z"));
    }

    @Test
    void rejectsMissingInconsistentAndUnrepresentableSchedules() {
        assertThatThrownBy(() -> RouteSchedule.resolve(start, null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RouteSchedule.resolve(null, 60, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RouteSchedule.resolve(start, 60, start.plusSeconds(7200)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RouteSchedule.resolve(start, null, start.plusNanos(1)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RouteSchedule.resolve(start, null, start.plusSeconds((Integer.MAX_VALUE + 1L) * 60)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RouteSchedule.resolve(Instant.MAX, 1, null)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -60})
    void rejectsNonPositiveDurations(int duration) {
        assertThatThrownBy(() -> RouteSchedule.resolve(start, duration, null)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-60, 0, 1, 59, 61})
    void requiresPositiveWholeMinutesWhenDerivingDuration(long seconds) {
        assertThatThrownBy(() -> RouteSchedule.resolve(start, null, start.plusSeconds(seconds)))
                .isInstanceOf(InvalidRequestException.class);
    }
}
