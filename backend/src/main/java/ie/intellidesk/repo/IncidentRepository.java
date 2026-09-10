package ie.intellidesk.repo;

import ie.intellidesk.domain.Incident;
import ie.intellidesk.domain.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface IncidentRepository
        extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Page<Incident> findByCreatedById(Long userId, Pageable pageable);

    List<Incident> findByStatusIn(List<IncidentStatus> statuses);

    // ---- dashboard aggregates ----

    @Query("select count(i) from Incident i where i.status not in ('RESOLVED', 'CLOSED')")
    long countOpen();

    @Query("select count(i) from Incident i where i.priority = 'P1' and i.status not in ('RESOLVED', 'CLOSED')")
    long countOpenCritical();

    @Query("select count(i) from Incident i where i.resolvedAt >= :since")
    long countResolvedSince(Instant since);

    @Query("select count(i) from Incident i where i.needsTriage = true and i.status not in ('RESOLVED', 'CLOSED')")
    long countNeedingTriage();

    List<Incident> findByCreatedAtAfter(Instant since);

    List<Incident> findByResolvedAtAfter(Instant since);

    @Query("select i.category, count(i) from Incident i where i.category is not null group by i.category order by count(i) desc")
    List<Object[]> countByCategory();

    @Query("select i.priority, count(i) from Incident i group by i.priority order by i.priority")
    List<Object[]> countByPriority();

    @Query("""
            select i from Incident i
            where i.resolvedAt is not null
            order by i.resolvedAt desc
            """)
    List<Incident> findResolved(Pageable pageable);

    /** Resolved incidents with usable resolution text — the corpus the ML service learns from. */
    @Query("""
            select i from Incident i
            where i.resolvedAt is not null
              and i.resolutionText is not null
              and length(i.resolutionText) > 10
            """)
    List<Incident> findResolvedWithText();
}
