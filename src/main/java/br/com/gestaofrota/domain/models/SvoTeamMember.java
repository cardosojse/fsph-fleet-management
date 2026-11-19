package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "svo_team_members")
public class SvoTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private SvoTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role_in_team", nullable = false, length = 100)
    private String roleInTeam;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SvoTeamMember() {
    }

    public SvoTeamMember(SvoTeam team, User user, String roleInTeam) {
        this.team = Objects.requireNonNull(team, "Equipe é obrigatória");
        this.user = Objects.requireNonNull(user, "Usuário é obrigatório");
        setRoleInTeam(roleInTeam);
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public SvoTeam getTeam() {
        return team;
    }

    public User getUser() {
        return user;
    }

    public String getRoleInTeam() {
        return roleInTeam;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public void setRoleInTeam(String roleInTeam) {
        if (roleInTeam == null || roleInTeam.isBlank()) {
            throw new InvalidRequestException("Função na equipe não pode ser vazia");
        }
        this.roleInTeam = roleInTeam.trim();
    }
}
