package ie.intellidesk.repo;

import ie.intellidesk.domain.IncidentUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentUpdateRepository extends JpaRepository<IncidentUpdate, Long> {
    List<IncidentUpdate> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
