package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Trip;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.DriverRepository;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.TripRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TripHistoryIntegrationTests extends PostgresIntegrationTest {
    @Autowired private TripRepository trips;
    @Autowired private VehicleRepository vehicles;
    @Autowired private DriverRepository drivers;
    @Autowired private UserRepository users;
    @Autowired private SectorRepository sectors;

    @Test
    void paginatesHistoryAndCountsWithAbsentOrPartialDateFilters() {
        var sector = sectors.save(new Sector("History " + UUID.randomUUID()));
        var user = users.save(new User(sector, "History driver", UUID.randomUUID() + "@example.test",
                "not-used-for-login", UserRole.MOTORISTA));
        var driver = drivers.save(new Driver(user));
        var vehicle = vehicles.save(new Vehicle(sector, UUID.randomUUID().toString().substring(0, 7),
                "History van", 8, "Institucional", 100));
        Instant start = Instant.parse("2026-01-10T12:00:00Z");
        var first = new Trip(vehicle, driver, start, 100);
        first.complete(start.plusSeconds(3600), 110);
        trips.saveAndFlush(first);
        var second = new Trip(vehicle, driver, start.plusSeconds(7200), 110);
        second.complete(start.plusSeconds(10800), 120);
        trips.saveAndFlush(second);
        var page = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "startedAt"));

        // A full page forces the separate count query as well as the data query.
        var unfiltered = trips.findHistory(null, null, null, null, page);
        assertThat(unfiltered.getTotalElements()).isGreaterThanOrEqualTo(2);
        var resourceFiltered = trips.findHistory(vehicle.getId(), driver.getId(), null, null, page);
        assertThat(resourceFiltered.getTotalElements()).isEqualTo(2);
        assertThat(resourceFiltered.getContent()).extracting(Trip::getId).containsExactly(second.getId());
        assertThat(trips.findHistory(vehicle.getId(), null, start.plusSeconds(3600), null, page).getTotalElements())
                .isEqualTo(1);
        assertThat(trips.findHistory(null, driver.getId(), null, start.plusSeconds(3600), page).getTotalElements())
                .isEqualTo(1);
        assertThat(trips.findHistory(vehicle.getId(), driver.getId(), start, start.plusSeconds(7200), page).getTotalElements())
                .isEqualTo(2);
    }
}
