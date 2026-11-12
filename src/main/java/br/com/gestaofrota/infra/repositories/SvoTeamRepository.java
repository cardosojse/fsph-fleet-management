package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.models.SvoTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SvoTeamRepository extends JpaRepository<SvoTeam, UUID> {

    List<SvoTeam> findAllByOrderByNameAsc();

    List<SvoTeam> findByActiveTrueOrderByNameAsc();

    Optional<SvoTeam> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("SELECT t FROM SvoTeam t LEFT JOIN FETCH t.members m LEFT JOIN FETCH m.user WHERE t.id = :id")
    Optional<SvoTeam> findWithMembersById(@Param("id") UUID id);
}
