package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CreateDriverRequest;
import br.com.gestaofrota.api.v1.dtos.requests.RegisterDriverLicenseRequest;
import br.com.gestaofrota.api.v1.dtos.responses.DriverLicenseResponse;
import br.com.gestaofrota.api.v1.dtos.responses.DriverResponse;
import br.com.gestaofrota.application.services.DriverLicenseService;
import br.com.gestaofrota.application.services.DriverService;
import br.com.gestaofrota.domain.enums.DriverStatus;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Sector;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService drivers;
    private final DriverLicenseService licenses;

    public DriverController(DriverService drivers, DriverLicenseService licenses) {
        this.drivers = drivers;
        this.licenses = licenses;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public List<DriverResponse> list() {
        return drivers.list().stream().map(DriverResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public DriverResponse find(@PathVariable UUID id) {
        return DriverResponse.from(drivers.find(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DRIVERS_WRITE')")
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody CreateDriverRequest request) {
        DriverResponse result = DriverResponse.from(drivers.create(request.userId()));
        return ResponseEntity.created(URI.create("/api/v1/drivers/" + result.id())).body(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DRIVERS_WRITE')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        drivers.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/licenses")
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public List<DriverLicenseResponse> listLicenses(@PathVariable UUID id) {
        return licenses.list(id).stream().map(DriverLicenseResponse::from).toList();
    }

    @GetMapping("/{id}/licenses/{licenseId}")
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public DriverLicenseResponse findLicense(@PathVariable UUID id, @PathVariable UUID licenseId) {
        return DriverLicenseResponse.from(licenses.find(id, licenseId));
    }

    @PostMapping("/{id}/licenses")
    @PreAuthorize("hasAuthority('DRIVERS_WRITE')")
    public ResponseEntity<DriverLicenseResponse> registerLicense(
            @PathVariable UUID id, @Valid @RequestBody RegisterDriverLicenseRequest request
    ) {
        DriverLicenseResponse result = DriverLicenseResponse.from(licenses.register(
                id,
                request.licenseNumber(),
                request.category(),
                request.issuedOn(),
                request.expiresOn()
            )
        );

        return ResponseEntity.created(URI.create("/api/v1/drivers/" + id + "/licenses/" + result.id())).body(result);
    }

    @GetMapping("find/{status}")
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public List<Driver> findStatus(@PathVariable Boolean active){
        return drivers.findByActive(active);
    }

    @GetMapping("find/{role}")
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public List<Driver> findByRole (@PathVariable UserRole role){
        return drivers.findByRole(role);
    }

    @GetMapping("find/{sector}")
    @PreAuthorize("hasAuthority('DRIVERS_READ')")
    public List<Driver> findByRole (@PathVariable Sector sector){
        return drivers.findBySector(sector);
    }



}