package ie.intellidesk.web.dto;

import ie.intellidesk.domain.*;
import ie.intellidesk.ml.MlContract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request and response shapes for the REST API. */
public final class Dtos {

    private Dtos() {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    public record CurrentUser(Long id, String name, String email, Role role) {
        public static CurrentUser from(AppUser u) {
            return new CurrentUser(u.getId(), u.getName(), u.getEmail(), u.getRole());
        }
    }

    public record CreateIncidentRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull Impact impact,
            @NotNull Urgency urgency) {
    }

    public record AddNoteRequest(@NotBlank @Size(max = 5000) String comment) {
    }

    public record StatusChangeRequest(@NotNull IncidentStatus status) {
    }

    public record ResolveRequest(@NotBlank @Size(max = 5000) String resolutionText) {
    }

    public record AssignRequest(@NotNull Long teamId) {
    }

    /** Row in the incident queue — deliberately lighter than the detail view. */
    public record IncidentSummary(
            Long id,
            String reference,
            String title,
            String category,
            Priority priority,
            IncidentStatus status,
            boolean needsTriage,
            String assignedTeam,
            String createdBy,
            Instant createdAt,
            Double predictedResolutionHours,
            Double slaBreachRisk) {

        public static IncidentSummary from(Incident i) {
            return new IncidentSummary(
                    i.getId(), i.getReference(), i.getTitle(), i.getCategory(),
                    i.getPriority(), i.getStatus(), i.isNeedsTriage(),
                    i.getAssignedTeam() == null ? null : i.getAssignedTeam().getName(),
                    i.getCreatedBy().getName(), i.getCreatedAt(),
                    i.getPredictedResolutionHours(), i.getSlaBreachRisk());
        }
    }

    public record ActivityEntry(
            Instant at, String user, String comment, IncidentStatus statusAfter, boolean system) {

        public static ActivityEntry from(IncidentUpdate u) {
            return new ActivityEntry(
                    u.getCreatedAt(),
                    u.getUser() == null ? "System" : u.getUser().getName(),
                    u.getComment(), u.getStatusAfter(), u.isSystemGenerated());
        }
    }

    public record IncidentDetail(
            IncidentSummary summary,
            String description,
            Impact impact,
            Urgency urgency,
            String subcategory,
            Double categoryConfidence,
            Integer slaTargetHours,
            Instant resolvedAt,
            String resolutionText,
            Double actualResolutionHours,
            List<MlContract.SimilarIncident> similarIncidents,
            List<String> suggestedSteps,
            List<ActivityEntry> activity,
            List<IncidentStatus> allowedTransitions) {
    }

    public record CategoryCount(String category, long count) {
    }

    public record PriorityCount(Priority priority, long count) {
    }

    public record TrendPoint(String date, long created, long resolved) {
    }

    public record Dashboard(
            long openIncidents,
            long criticalOpen,
            long resolvedToday,
            long needingTriage,
            Double averageResolutionHours,
            List<CategoryCount> byCategory,
            List<PriorityCount> byPriority,
            List<TrendPoint> trend,
            boolean mlServiceUp) {
    }

    public record TeamView(Long id, String name, String handlesCategory) {
        public static TeamView from(Team t) {
            return new TeamView(t.getId(), t.getName(), t.getHandlesCategory());
        }
    }
}
