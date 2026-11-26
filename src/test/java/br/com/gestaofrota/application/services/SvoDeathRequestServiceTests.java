package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.CancelSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreateSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.requests.TriageSvoDeathRequest;
import br.com.gestaofrota.domain.enums.DeathLocationType;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.SvoRequestStatus;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.SvoDeathRequest;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SvoDeathRequestRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SvoDeathRequestServiceTests {

    @Mock
    private SvoDeathRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private SvoDeathRequestService requestService;

    private Sector sector;
    private User requesterUser;
    private AuthenticatedUser requesterActor;
    private User atendenteUser;
    private AuthenticatedUser atendenteActor;

    @BeforeEach
    void setUp() {
        requestService = new SvoDeathRequestService(requestRepository, userRepository, auditLogService);
        sector = new Sector("Recepção Externa");

        UUID requesterId = UUID.randomUUID();
        requesterUser = new User(sector, "Maria Solicitante", "maria@hospital.se.gov.br", "hash", UserRole.EMPLOYEE);
        ReflectionTestUtils.setField(requesterUser, "id", requesterId);

        requesterActor = new AuthenticatedUser(
                requesterId,
                UUID.randomUUID(),
                "Maria Solicitante",
                "maria@hospital.se.gov.br",
                "hash",
                UserRole.EMPLOYEE,
                true,
                Set.of(Permission.SVO_REQUESTS_WRITE)
        );

        UUID atendenteId = UUID.randomUUID();
        atendenteUser = new User(sector, "João Atendente", "joao.atendente@svo.se.gov.br", "hash", UserRole.ATENDENTE_SVO);
        ReflectionTestUtils.setField(atendenteUser, "id", atendenteId);

        atendenteActor = new AuthenticatedUser(
                atendenteId,
                UUID.randomUUID(),
                "João Atendente",
                "joao.atendente@svo.se.gov.br",
                "hash",
                UserRole.ATENDENTE_SVO,
                true,
                Set.of(Permission.SVO_REQUESTS_READ, Permission.SVO_REQUESTS_WRITE, Permission.SVO_REQUESTS_MANAGE)
        );
    }

    @Test
    @DisplayName("Deve registrar solicitação SVO com protocolo gerado e status PENDING_TRIAGE com sucesso")
    void createRequestSuccessfully() {
        CreateSvoDeathRequest request = new CreateSvoDeathRequest(
                "José dos Santos",
                78,
                "MALE",
                Instant.now().minusSeconds(3600),
                DeathLocationType.DOMICILE,
                "Rua A, 123",
                "Centro",
                "Aracaju",
                "Próximo à praça principal",
                "Ana Santos (Filha)",
                "79999887766",
                "FILHA",
                "Óbito em residência por causas naturais (hipertensão crônica)",
                false,
                "Família no local com documentos"
        );

        when(userRepository.findById(requesterActor.id())).thenReturn(Optional.of(requesterUser));
        when(requestRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(requestRepository.existsByProtocolNumberIgnoreCase(anyString())).thenReturn(false);
        when(requestRepository.save(any(SvoDeathRequest.class))).thenAnswer(invocation -> {
            SvoDeathRequest req = invocation.getArgument(0);
            ReflectionTestUtils.setField(req, "id", UUID.randomUUID());
            return req;
        });

        SvoDeathRequest created = requestService.create(request, requesterActor);

        assertThat(created).isNotNull();
        assertThat(created.getProtocolNumber()).startsWith("SVO-");
        assertThat(created.getStatus()).isEqualTo(SvoRequestStatus.PENDING_TRIAGE);
        assertThat(created.getRequester()).isEqualTo(requesterUser);
        assertThat(created.getDeceasedName()).isEqualTo("José dos Santos");
        assertThat(created.getAddress()).isEqualTo("Rua A, 123");
        verify(auditLogService).record(eq(requesterActor), eq("SVO_REQUEST_CREATED"), eq("SVO_REQUEST"), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve cancelar solicitação pendente com sucesso pelo próprio solicitante")
    void cancelRequestSuccessfullyByRequester() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Desconhecido",
                null,
                null,
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua B, 45",
                "Siqueira Campos",
                "Aracaju",
                null,
                "Carlos",
                "79988776655",
                "VIZINHO",
                "Mal súbito",
                false,
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);

        when(requestRepository.lockById(reqId)).thenReturn(Optional.of(deathRequest));
        when(requestRepository.save(deathRequest)).thenReturn(deathRequest);

        CancelSvoDeathRequest cancelReq = new CancelSvoDeathRequest("Família acionou médico particular");
        SvoDeathRequest canceled = requestService.cancel(reqId, cancelReq, requesterActor);

        assertThat(canceled.getStatus()).isEqualTo(SvoRequestStatus.CANCELED);
        assertThat(canceled.getRejectionReason()).contains("Família acionou médico particular");
        verify(auditLogService).record(eq(requesterActor), eq("SVO_REQUEST_CANCELED"), eq("SVO_REQUEST"), eq(reqId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve impedir cancelamento de solicitação que não esteja PENDING_TRIAGE")
    void throwWhenCancelingNonPendingRequest() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Falecido",
                60,
                "MALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua C, 10",
                "Luzia",
                "Aracaju",
                null,
                "Contato",
                "79999999999",
                null,
                null,
                false,
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);
        deathRequest.triage(atendenteUser, true, null); // Status vira TRIAGED

        when(requestRepository.lockById(reqId)).thenReturn(Optional.of(deathRequest));

        assertThatThrownBy(() -> requestService.cancel(reqId, new CancelSvoDeathRequest("Motivo"), requesterActor))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("não pode ser cancelada no status atual");
    }

    @Test
    @DisplayName("Deve impedir que outro usuário sem permissão cancele a solicitação de terceiros")
    void throwWhenCancelingByAnotherNonPrivilegedUser() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Falecido",
                60,
                "MALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua C, 10",
                "Luzia",
                "Aracaju",
                null,
                "Contato",
                "79999999999",
                null,
                null,
                false,
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);

        AuthenticatedUser otherUser = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Outro Usuario",
                "outro@example.com",
                "hash",
                UserRole.EMPLOYEE,
                true,
                Set.of(Permission.SVO_REQUESTS_WRITE)
        );

        when(requestRepository.lockById(reqId)).thenReturn(Optional.of(deathRequest));

        assertThatThrownBy(() -> requestService.cancel(reqId, new CancelSvoDeathRequest("Motivo"), otherUser))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Você não tem permissão para cancelar uma solicitação aberta por outro usuário");
    }

    @Test
    @DisplayName("Deve triar e aprovar solicitação SVO com sucesso")
    void triageApproveSuccessfully() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Falecido",
                70,
                "FEMALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua D, 20",
                "Grageru",
                "Aracaju",
                null,
                "Declarante",
                "79988887777",
                "FILHO",
                "Parada cardiorrespiratória em domicílio",
                false,
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);

        when(requestRepository.lockById(reqId)).thenReturn(Optional.of(deathRequest));
        when(userRepository.findById(atendenteActor.id())).thenReturn(Optional.of(atendenteUser));
        when(requestRepository.save(deathRequest)).thenReturn(deathRequest);

        TriageSvoDeathRequest triageReq = new TriageSvoDeathRequest(true, null);
        SvoDeathRequest triaged = requestService.triage(reqId, triageReq, atendenteActor);

        assertThat(triaged.getStatus()).isEqualTo(SvoRequestStatus.TRIAGED);
        assertThat(triaged.getTriagedBy()).isEqualTo(atendenteUser);
        assertThat(triaged.getTriagedAt()).isNotNull();
        verify(auditLogService).record(eq(atendenteActor), eq("SVO_REQUEST_TRIAGED"), eq("SVO_REQUEST"), eq(reqId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve rejeitar aprovação no SVO se houver suspeita de violência e orientar IML")
    void throwWhenApprovingWithSuspectedViolence() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Falecido",
                40,
                "MALE",
                Instant.now(),
                DeathLocationType.PUBLIC_WAY,
                "Avenida Santos Dumont, s/n",
                "Atalaia",
                "Aracaju",
                null,
                "Testemunha",
                "79988887777",
                "TESTEMUNHA",
                "Queda de altura suspeita / possível trauma",
                true, // hasSuspectedViolence = true
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);

        when(requestRepository.lockById(reqId)).thenReturn(Optional.of(deathRequest));
        when(userRepository.findById(atendenteActor.id())).thenReturn(Optional.of(atendenteUser));

        TriageSvoDeathRequest triageReq = new TriageSvoDeathRequest(true, null);

        assertThatThrownBy(() -> requestService.triage(reqId, triageReq, atendenteActor))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("competência exclusiva do IML");
    }

    @Test
    @DisplayName("Deve triar e recusar solicitação SVO com justificativa")
    void triageRejectSuccessfullyWithReason() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Falecido",
                50,
                "MALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua E, 30",
                "Jardins",
                "Aracaju",
                null,
                "Declarante",
                "79988887777",
                null,
                null,
                false,
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);

        when(requestRepository.lockById(reqId)).thenReturn(Optional.of(deathRequest));
        when(userRepository.findById(atendenteActor.id())).thenReturn(Optional.of(atendenteUser));
        when(requestRepository.save(deathRequest)).thenReturn(deathRequest);

        TriageSvoDeathRequest triageReq = new TriageSvoDeathRequest(false, "Paciente com assistência médica ativa nas últimas 24h, DO deve ser emitida pelo médico assistente");
        SvoDeathRequest rejected = requestService.triage(reqId, triageReq, atendenteActor);

        assertThat(rejected.getStatus()).isEqualTo(SvoRequestStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).contains("médico assistente");
        verify(auditLogService).record(eq(atendenteActor), eq("SVO_REQUEST_TRIAGED"), eq("SVO_REQUEST"), eq(reqId.toString()), anyString());
    }

    @Test
    @DisplayName("Deve impedir que usuário comum visualize solicitação alheia por ID")
    void findByIdRestrictsOtherUsers() {
        SvoDeathRequest deathRequest = new SvoDeathRequest(
                "SVO-20261118-0001",
                requesterUser,
                "Falecido",
                50,
                "MALE",
                Instant.now(),
                DeathLocationType.DOMICILE,
                "Rua E, 30",
                "Jardins",
                "Aracaju",
                null,
                "Declarante",
                "79988887777",
                null,
                null,
                false,
                null
        );
        UUID reqId = UUID.randomUUID();
        ReflectionTestUtils.setField(deathRequest, "id", reqId);

        AuthenticatedUser otherUser = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Outro Usuario",
                "outro@example.com",
                "hash",
                UserRole.EMPLOYEE,
                true,
                Set.of(Permission.SVO_REQUESTS_WRITE)
        );

        when(requestRepository.findById(reqId)).thenReturn(Optional.of(deathRequest));

        assertThatThrownBy(() -> requestService.findById(reqId, otherUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
