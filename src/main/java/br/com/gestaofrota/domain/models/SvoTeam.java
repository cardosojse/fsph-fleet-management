package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "svo_teams")
public class SvoTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SvoTeamMember> members = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SvoTeam() {
    }

    public SvoTeam(String name, String description) {
        rename(name);
        updateDescription(description);
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public List<SvoTeamMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Nome da equipe não pode ser vazio");
        }
        this.name = name.trim();
    }

    public void updateDescription(String description) {
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public SvoTeamMember addMember(User user, String roleInTeam) {
        Objects.requireNonNull(user, "Usuário não pode ser nulo");
        if (!user.isActive()) {
            throw new ConflictException("Usuário inativo não pode ser adicionado à equipe");
        }

        for (SvoTeamMember existing : members) {
            if (existing.getUser().getId().equals(user.getId()) && existing.isActive()) {
                throw new ConflictException("Este usuário já é membro ativo desta equipe");
            }
        }

        SvoTeamMember newMember = new SvoTeamMember(this, user, roleInTeam);
        this.members.add(newMember);
        return newMember;
    }

    public void removeMember(UUID memberId) {
        Objects.requireNonNull(memberId, "Identificador do membro não pode ser nulo");
        members.removeIf(member -> member.getId().equals(memberId));
    }
}
