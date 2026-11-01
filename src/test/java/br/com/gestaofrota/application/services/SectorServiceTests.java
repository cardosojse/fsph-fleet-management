package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import br.com.gestaofrota.infra.repositories.TransportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectorServiceTests {

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private TransportRepository transportRepository;

    @InjectMocks
    private SectorService sectorService;

    @Test
    void rejectsDuplicateSectorName() {
        when(sectorRepository.existsByNameIgnoreCase("Financeiro")).thenReturn(true);

        assertThrows(ConflictException.class, () -> sectorService.create(" Financeiro "));
        verify(sectorRepository, never()).save(any());
    }

    @Test
    void rejectsDeletionWhenSectorHasUsers() {
        UUID id = UUID.randomUUID();
        Sector sector = new Sector("Financeiro");
        when(sectorRepository.findById(id)).thenReturn(Optional.of(sector));
        when(userRepository.existsBySectorId(id)).thenReturn(true);

        assertThrows(ConflictException.class, () -> sectorService.delete(id));
        verify(sectorRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletionWhenSectorHasVehicles() {
        UUID id = UUID.randomUUID();
        when(sectorRepository.findById(id)).thenReturn(Optional.of(new Sector("Financeiro")));
        when(vehicleRepository.existsBySectorId(id)).thenReturn(true);

        assertThrows(ConflictException.class, () -> sectorService.delete(id));
        verify(sectorRepository, never()).delete(any());
    }

    @Test
    void rejectsDeletionWhenSectorHasTransportHistory() {
        UUID id = UUID.randomUUID();
        when(sectorRepository.findById(id)).thenReturn(Optional.of(new Sector("Financeiro")));
        when(transportRepository.existsBySectorId(id)).thenReturn(true);

        assertThrows(ConflictException.class, () -> sectorService.delete(id));
        verify(sectorRepository, never()).delete(any());
    }
}
