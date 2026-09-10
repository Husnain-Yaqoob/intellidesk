package ie.intellidesk.repo;

import ie.intellidesk.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByHandlesCategoryIgnoreCase(String category);
    Optional<Team> findByNameIgnoreCase(String name);
}
