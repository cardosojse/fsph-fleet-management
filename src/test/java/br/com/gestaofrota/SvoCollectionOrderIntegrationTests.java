package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.models.*;
import br.com.gestaofrota.infra.repositories.*;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
class SvoCollectionOrderIntegrationTests extends PostgresIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired SectorRepository sectors; @Autowired UserRepository users; @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles; @Autowired SvoTeamRepository teams;
    @DynamicPropertySource static void isolatedSchema(DynamicPropertyRegistry registry) throws Exception {
        String schema="svo_"+UUID.randomUUID().toString().replace("-", "");
        try(var c=DriverManager.getConnection(jdbcUrl(),databaseUsername(),databasePassword());var s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        registry.add("spring.datasource.hikari.schema",()->schema);
        registry.add("spring.flyway.schemas",()->schema);
        registry.add("spring.flyway.default-schema",()->schema);
    }
    record Fixture(AuthenticatedUser attendant,AuthenticatedUser manager,AuthenticatedUser collector,
                   AuthenticatedUser other,AuthenticatedUser foreign,UUID driverId,UUID vehicleId,UUID teamId){}
    private AuthenticatedUser actor(Sector sector,UserRole role,Permission... permissions){
        var u=users.save(new User(sector,role+" Teste",UUID.randomUUID()+"@example.test","unused",role));
        return new AuthenticatedUser(u.getId(),sector.getId(),u.getName(),u.getEmail(),u.getPassword(),role,true,Set.of(permissions));
    }
    private Fixture fixture(){
        var s=sectors.save(new Sector("SVO teste "+UUID.randomUUID()));
        var attendant=actor(s,UserRole.ATENDENTE_SVO,Permission.SVO_REQUESTS_WRITE,Permission.SVO_REQUESTS_READ,Permission.SVO_REQUESTS_MANAGE,Permission.SVO_ORDERS_READ,Permission.SVO_ORDERS_NOTES);
        var manager=actor(s,UserRole.GESTOR_SVO,Permission.SVO_ORDERS_READ,Permission.SVO_ORDERS_MANAGE,Permission.SVO_ORDERS_REPORT);
        var driverActor=actor(s,UserRole.MOTORISTA,Permission.SVO_ORDERS_READ,Permission.SVO_ORDERS_EXECUTE);
        var d=drivers.save(new Driver(users.findById(driverActor.id()).orElseThrow()));
        var collector=actor(s,UserRole.EQUIPE_COLETA_SVO,Permission.SVO_ORDERS_READ,Permission.SVO_ORDERS_EXECUTE);
        var team=new SvoTeam("Equipe "+UUID.randomUUID(),null);team.addMember(users.findById(collector.id()).orElseThrow(),"Coleta");team=teams.save(team);
        var v=vehicles.save(new Vehicle(s,"S"+UUID.randomUUID().toString().replace("-", "").substring(0,6),"Coleta SVO",3,"SVO",0));
        return new Fixture(attendant,manager,collector,actor(s,UserRole.EQUIPE_COLETA_SVO,Permission.SVO_ORDERS_READ,Permission.SVO_ORDERS_EXECUTE),
                actor(sectors.save(new Sector("Outro "+UUID.randomUUID())),UserRole.ATENDENTE_SVO,Permission.SVO_ORDERS_READ,Permission.SVO_ORDERS_NOTES),d.getId(),v.getId(),team.getId());
    }
    private String body(Object body){return mapper.writeValueAsString(body);}
    private tools.jackson.databind.JsonNode generate(Fixture f) throws Exception {
        String json=mvc.perform(post("/api/v1/svo/requests").with(user(f.attendant())).contentType(MediaType.APPLICATION_JSON).content("""
            {"occurredAt":"2026-01-01T10:00:00Z","deathLocationType":"DOMICILE","address":"Rua teste",
             "neighborhood":"Centro","city":"Aracaju","contactName":"Contato teste","contactPhone":"79999990000"}
            """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var requestId=mapper.readTree(json).path("id").asText();
        mvc.perform(post("/api/v1/svo/requests/{id}/order",requestId).with(user(f.attendant()))).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/svo/requests/{id}/triage",requestId).with(user(f.attendant())).contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}")).andExpect(status().isOk());
        var order=mapper.readTree(mvc.perform(post("/api/v1/svo/requests/{id}/order",requestId).with(user(f.attendant())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/svo/requests/{id}/order",requestId).with(user(f.attendant())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(order.path("id").asText())).andExpect(jsonPath("$.events.length()").value(1));
        mvc.perform(get("/api/v1/svo/requests/{id}",requestId).with(user(f.attendant())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDER_GENERATED"));
        return order;
    }
    private tools.jackson.databind.JsonNode assign(Fixture f,tools.jackson.databind.JsonNode o) throws Exception {
        return mapper.readTree(mvc.perform(put("/api/v1/svo/orders/{id}/assignment",o.path("id").asText()).with(user(f.manager()))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("expectedVersion",o.path("version").asLong(),"driverId",f.driverId(),"vehicleId",f.vehicleId(),
                "teamId",f.teamId(),"scheduledAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(60).toString(),"plannedReturnAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(3660).toString(),"collectionDeadlineAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(1860).toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED")).andReturn().getResponse().getContentAsString());
    }
    private tools.jackson.databind.JsonNode advance(AuthenticatedUser actor,tools.jackson.databind.JsonNode o,String status) throws Exception {
        return mapper.readTree(mvc.perform(patch("/api/v1/svo/orders/{id}/status",o.path("id").asText()).with(user(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("expectedVersion",o.path("version").asLong(),"status",status,"observation","Registro em campo"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void persistsTheSameOrderForThreeProfilesAndComputesIndependentStageAverages() throws Exception {
        var f=fixture();var o=assign(f,generate(f));String id=o.path("id").asText();
        mvc.perform(get("/api/v1/svo/orders").with(user(f.collector()))).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get("/api/v1/svo/orders").with(user(f.other()))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        for(var actor:List.of(f.other(),f.foreign())){
            mvc.perform(get("/api/v1/svo/orders/{id}",id).with(user(actor))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/svo/orders/{id}/status",id).with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("expectedVersion",o.path("version").asLong(),"status","IN_PROGRESS")))).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/v1/svo/orders/{id}/status",id).with(user(f.collector())).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("expectedVersion",o.path("version").asLong(),"status","DELIVERED")))).andExpect(status().isConflict());
        o=advance(f.collector(),o,"IN_PROGRESS");o=advance(f.collector(),o,"COLLECTED");
        mvc.perform(get("/api/v1/svo/orders/report").param("teamId",f.teamId().toString()).with(user(f.manager())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.collectionCount").value(1)).andExpect(jsonPath("$.deliveryCount").value(0))
                .andExpect(jsonPath("$.averageRequestToCollectionMinutes").isNumber()).andExpect(jsonPath("$.averageCollectionToDeliveryMinutes").isEmpty());
        var beforeNote=o;
        o=mapper.readTree(mvc.perform(post("/api/v1/svo/orders/{id}/notes",id).with(user(f.attendant())).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("expectedVersion",o.path("version").asLong(),"text","Contato com a família registrado"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/svo/orders/{id}/notes",id).with(user(f.attendant())).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("expectedVersion",beforeNote.path("version").asLong(),"text","Gravação desatualizada")))).andExpect(status().isConflict());
        o=advance(f.collector(),o,"DELIVERED");
        mvc.perform(patch("/api/v1/svo/orders/{id}/status",id).with(user(f.collector())).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("expectedVersion",o.path("version").asLong(),"status","DELIVERED")))).andExpect(status().isConflict());
        var reopened=mapper.readTree(mvc.perform(get("/api/v1/svo/orders/{id}",id).with(user(f.attendant())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(6)).andReturn().getResponse().getContentAsString());
        assertThat(reopened.path("events").get(4).path("authorId").asText()).isEqualTo(f.attendant().id().toString());
        assertThat(reopened.path("events").get(4).path("text").asText()).isEqualTo("Contato com a família registrado");
        for(var e:reopened.path("events")){assertThat(e.path("authorName").asText()).isNotBlank();assertThat(Instant.parse(e.path("occurredAt").asText())).isBeforeOrEqualTo(Instant.now());}
        var report=mapper.readTree(mvc.perform(get("/api/v1/svo/orders/report").param("vehicleId",f.vehicleId().toString()).with(user(f.manager())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(report.path("collectionCount").asLong()).isEqualTo(1);assertThat(report.path("deliveryCount").asLong()).isEqualTo(1);
        for(String metric:List.of("RequestToCollection","CollectionToDelivery","RequestToDelivery"))
            assertThat(report.path("average"+metric+"Minutes").asDouble()).isEqualTo(reopened.path(Character.toLowerCase(metric.charAt(0))+metric.substring(1)+"Minutes").asDouble());
        assertThatThrownBy(()->jdbc.update("UPDATE svo_order_events SET text='overwrite' WHERE order_id=?",UUID.fromString(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void serializesTwoPeopleSavingTheSameVersion() throws Exception {
        var f=fixture();var o=generate(f);String id=o.path("id").asText();
        try(var pool=Executors.newFixedThreadPool(2)){
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);List<Future<Integer>> calls=new ArrayList<>();
            for(int i=0;i<2;i++)calls.add(pool.submit(()->{ready.countDown();start.await();return mvc.perform(post("/api/v1/svo/orders/{id}/notes",id)
                .with(user(f.attendant())).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("expectedVersion",0,"text","Uma única providência"))))
                .andReturn().getResponse().getStatus();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(calls.get(0).get(15,TimeUnit.SECONDS),calls.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void blocksConflictingReservationsInBothDirectionsAndReleasesOnDelivery() throws Exception {
        var f=fixture();var o=assign(f,generate(f));String id=o.path("id").asText();
        String sql="INSERT INTO routes(id,vehicle_id,driver_id,created_by_user_id,planned_start_at,planned_duration_minutes,planned_return_at) VALUES(?,?,?,?,now()+interval '1 minute',30,now()+interval '31 minutes')";
        assertThatThrownBy(()->jdbc.update(sql,UUID.randomUUID(),f.vehicleId(),f.driverId(),f.manager().id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var second=generate(f);
        mvc.perform(put("/api/v1/svo/orders/{id}/assignment",second.path("id").asText()).with(user(f.manager())).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("expectedVersion",0,"driverId",f.driverId(),"vehicleId",f.vehicleId(),"scheduledAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(60).toString(),"plannedReturnAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(1860).toString())))).andExpect(status().isConflict());
        o=advance(f.collector(),o,"IN_PROGRESS");o=advance(f.collector(),o,"COLLECTED");advance(f.collector(),o,"DELIVERED");
        jdbc.update(sql,UUID.randomUUID(),f.vehicleId(),f.driverId(),f.manager().id());
        mvc.perform(put("/api/v1/svo/orders/{id}/assignment",second.path("id").asText()).with(user(f.manager())).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("expectedVersion",0,"driverId",f.driverId(),"vehicleId",f.vehicleId(),"scheduledAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(60).toString(),"plannedReturnAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusSeconds(1860).toString())))).andExpect(status().isConflict());
    }
}
