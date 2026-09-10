package ie.intellidesk.repo;

import ie.intellidesk.domain.Incident;
import ie.intellidesk.domain.IncidentStatus;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the agent's incident queue. */
public final class IncidentSpecs {

    private IncidentSpecs() {
    }

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> openOnly(boolean openOnly) {
        return (root, query, cb) -> openOnly
                ? cb.not(root.get("status").in(IncidentStatus.RESOLVED, IncidentStatus.CLOSED))
                : cb.conjunction();
    }

    public static Specification<Incident> hasCategory(String category) {
        return (root, query, cb) -> (category == null || category.isBlank())
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("category")), category.toLowerCase());
    }

    public static Specification<Incident> matches(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            String like = "%" + q.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("reference")), like));
        };
    }

    public static Specification<Incident> createdBy(Long userId) {
        return (root, query, cb) ->
                userId == null ? cb.conjunction() : cb.equal(root.get("createdBy").get("id"), userId);
    }
}
