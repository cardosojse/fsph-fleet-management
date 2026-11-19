package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.AddTeamMemberRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreateSvoTeamRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateSvoTeamRequest;
import br.com.gestaofrota.api.v1.dtos.responses.SvoTeamMemberResponse;
import br.com.gestaofrota.api.v1.dtos.responses.SvoTeamResponse;
import br.com.gestaofrota.application.services.SvoTeamService;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/svo/teams")
public class SvoTeamController {

    private final SvoTeamService teamService;

    public SvoTeamController(SvoTeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SVO_TEAMS_READ')")
    public List<SvoTeamResponse> list(@RequestParam(required = false) Boolean activeOnly) {
        return teamService.list(activeOnly).stream()
                .map(SvoTeamResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SVO_TEAMS_READ')")
    public SvoTeamResponse find(@PathVariable UUID id) {
        return SvoTeamResponse.from(teamService.find(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SVO_TEAMS_WRITE')")
    public ResponseEntity<SvoTeamResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateSvoTeamRequest request
    ) {
        SvoTeamResponse result = SvoTeamResponse.from(
                teamService.create(request.name(), request.description(), request.initialMembers(), user)
        );
        return ResponseEntity.created(URI.create("/api/v1/svo/teams/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SVO_TEAMS_WRITE')")
    public SvoTeamResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSvoTeamRequest request
    ) {
        return SvoTeamResponse.from(teamService.update(id, request.name(), request.description(), user));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('SVO_TEAMS_WRITE')")
    public ResponseEntity<Void> deactivate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        teamService.deactivate(id, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('SVO_TEAMS_WRITE')")
    public ResponseEntity<Void> reactivate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        teamService.reactivate(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('SVO_TEAMS_WRITE')")
    public ResponseEntity<SvoTeamMemberResponse> addMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AddTeamMemberRequest request
    ) {
        SvoTeamMemberResponse result = SvoTeamMemberResponse.from(
                teamService.addMember(id, request.userId(), request.roleInTeam(), user)
        );
        return ResponseEntity.created(URI.create("/api/v1/svo/teams/" + id + "/members/" + result.id())).body(result);
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasAuthority('SVO_TEAMS_WRITE')")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID memberId
    ) {
        teamService.removeMember(id, memberId, user);
        return ResponseEntity.noContent().build();
    }
}
