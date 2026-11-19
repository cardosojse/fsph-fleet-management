package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction: exercise DTO mapping after real service transactions close.
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
class ReleasedModuleHttpIntegrationTests extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SectorRepository sectors;
    @Autowired UserRepository users;
    @Autowired VehicleRepository vehicles;

    @Test
    void createsAndReadsBoundFuelCardsThroughHttpAndPostgres() throws Exception {
        var actor = actor();
        var vehicle = vehicles.save(new Vehicle(null, UUID.randomUUID().toString().replace("-", "").substring(0,7), "Release van", 8, "Transporte", 0));
        String number = "release-" + UUID.randomUUID();
        String body = """
                {"cardNumber":"%s","cardholderName":"Release fixture","expirationDate":"2030-12-31",
                 "totalLimit":100,"availableBalance":80,"vehicleId":"%s"}
                """.formatted(number, vehicle.getId());
        String json = mvc.perform(post("/api/v1/fuel-cards").with(user(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(json).path("id").asText();
        for (String path : new String[]{"/api/v1/fuel-cards/"+id, "/api/v1/fuel-cards/by-number/"+number, "/api/v1/fuel-cards/by-vehicle/"+vehicle.getId()}) {
            mvc.perform(get(path).with(user(actor))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.vehicle.id").value(vehicle.getId().toString()));
        }
        mvc.perform(get("/api/v1/fuel-cards").param("vehicleId", vehicle.getId().toString()).with(user(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/v1/fuel-cards").param("search", number).with(user(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(patch("/api/v1/fuel-cards/{id}/status", id).with(user(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void createsAndReadsSvoRequestsAndAuditWithoutDateFilters() throws Exception {
        var actor = actor();
        String body = """
                {"occurredAt":"2026-11-19T10:00:00Z","deathLocationType":"DOMICILE",
                 "address":"Local fixture address","neighborhood":"Local fixture","city":"Aracaju",
                 "contactName":"Local fixture contact","contactPhone":"79999990000"}
                """;
        String json = mvc.perform(post("/api/v1/svo/requests").with(user(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(json).path("id").asText();
        mvc.perform(get("/api/v1/svo/requests/{id}", id).with(user(actor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterName").value(actor.name()));
        for (String path : new String[]{"/api/v1/svo/requests", "/api/v1/svo/requests/my-requests", "/api/v1/svo/teams", "/api/v1/svo/shifts", "/api/v1/audit-logs"}) {
            mvc.perform(get(path).with(user(actor))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/svo/requests").param("city", "Aracaju").param("search", "Local fixture").with(user(actor)))
                .andExpect(status().isOk());
    }

    private AuthenticatedUser actor() {
        var sector = sectors.save(new Sector("Release HTTP " + UUID.randomUUID()));
        var saved = users.save(new User(sector,"Release operator",UUID.randomUUID()+"@example.test","unused",UserRole.GESTOR_FROTA));
        return new AuthenticatedUser(saved.getId(),sector.getId(),saved.getName(),saved.getEmail(),saved.getPassword(),saved.getRole(),true,EnumSet.allOf(Permission.class));
    }
}
