package ie.intellidesk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ie.intellidesk.domain.*;
import ie.intellidesk.ml.MlClient;
import ie.intellidesk.ml.MlContract;
import ie.intellidesk.repo.*;
import ie.intellidesk.triage.PriorityMatrix;
import ie.intellidesk.web.dto.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidents;
    private final IncidentUpdateRepository updates;
    private final TeamRepository teams;
    private final MlClient ml;
    private final ObjectMapper json;

    /**
     * Below this confidence the classifier's answer is not used to route the incident.
     * A wrong auto-assignment costs more than an unassigned one: it sits in the wrong
     * team's queue being ignored, instead of in the triage queue being looked at.
     */
    private final double confidenceThreshold;

    public IncidentService(IncidentRepository incidents,
                           IncidentUpdateRepository updates,
                           TeamRepository teams,
                           MlClient ml,
                           ObjectMapper json,
                           @Value("${intellidesk.ml.confidence-threshold:0.60}") double confidenceThreshold) {
        this.incidents = incidents;
        this.updates = updates;
        this.teams = teams;
        this.ml = ml;
        this.json = json;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Transactional
    public Incident create(Dtos.CreateIncidentRequest request, AppUser raisedBy) {
        Priority priority = PriorityMatrix.resolve(request.impact(), request.urgency());

        Incident incident = new Incident(
                request.title().trim(), request.description().trim(),
                request.impact(), request.urgency(), priority, raisedBy);

        incidents.saveAndFlush(incident);
        incident.setReference(reference(incident.getId()));

        log(incident, null, "Incident created with priority %s (impact %s x urgency %s)"
                .formatted(priority, request.impact(), request.urgency()), IncidentStatus.NEW);

        applyMlAnalysis(incident);

        return incidents.save(incident);
    }

    /**
     * Enriches the incident with whatever the ML service can tell us. Silent no-op when
     * the service is unreachable — see {@link MlClient}.
     */
    private void applyMlAnalysis(Incident incident) {
        Optional<MlContract.AnalyseResponse> maybe = ml.analyse(incident);

        if (maybe.isEmpty()) {
            incident.setNeedsTriage(true);
            log(incident, null, "Automatic classification unavailable — queued for manual triage", null);
            return;
        }

        MlContract.AnalyseResponse a = maybe.get();
        incident.setCategoryConfidence(a.confidence());
        incident.setMlModelVersion(a.modelVersion());

        if (a.confidence() < confidenceThreshold) {
            // Nothing derived from the model is stored for an incident it could not
            // classify — not the predicted time, not the SLA risk, not the neighbours.
            //
            // The predicted time and risk are computed from the predicted category,
            // which we have just decided not to trust. The neighbours are a subtler
            // case: the first version kept them, on the theory that a human triaging
            // the ticket would still want to see whatever resembled it. Testing showed
            // that was wrong. Vague text — "my computer is being weird" — matches on
            // one incidental word ("working", "home") and produces neighbours at ~0.40
            // that have nothing to do with the fault, while a genuine match on the same
            // corpus scores only ~0.44. No threshold separates those two.
            //
            // The vagueness that defeats the classifier defeats the similarity search
            // for the same reason. If we will not name the category, we should not
            // offer the advice either.
            incident.setNeedsTriage(true);
            log(incident, null, "Classified as %s but confidence was only %.0f%% — queued for manual triage"
                    .formatted(a.category(), a.confidence() * 100), null);
            return;
        }

        incident.setPredictedResolutionHours(a.predictedResolutionHours());
        incident.setSlaBreachRisk(a.slaBreachRisk());
        writeJson(a.similarIncidents(), incident::setMlSimilarJson);
        writeJson(a.suggestedSteps(), incident::setMlStepsJson);
        incident.setCategory(a.category());
        incident.setSubcategory(a.subcategory());
        log(incident, null, "Classified as %s / %s (%.0f%% confidence)"
                .formatted(a.category(), a.subcategory(), a.confidence() * 100), null);

        teams.findByHandlesCategoryIgnoreCase(a.category()).ifPresent(team -> {
            incident.setAssignedTeam(team);
            incident.setStatus(IncidentStatus.ASSIGNED);
            log(incident, null, "Assigned to " + team.getName(), IncidentStatus.ASSIGNED);
        });
    }

    @Transactional
    public Incident addNote(Long incidentId, AppUser author, String comment) {
        Incident incident = requireVisibleTo(incidentId, author);
        log(incident, author, comment, null);
        return incident;
    }

    /**
     * Loads an incident and checks the caller is allowed to see it.
     *
     * <p>Inside the transaction on purpose: {@code createdBy} is a lazy association and
     * {@code open-in-view} is off, so the ownership check has to happen while the session
     * is still open. Doing it in the controller would work in development and fail in
     * production, which is the worst kind of bug.
     */
    @Transactional(readOnly = true)
    public Incident requireVisibleTo(Long incidentId, AppUser user) {
        Incident incident = require(incidentId);
        boolean isAgent = user.getRole() == Role.AGENT;
        boolean isOwner = incident.getCreatedBy().getId().equals(user.getId());
        if (!isAgent && !isOwner) {
            throw new NotYoursException();
        }
        return incident;
    }

    /** Raised when an employee reaches for someone else's incident. */
    public static class NotYoursException extends RuntimeException {
        public NotYoursException() {
            super("That incident belongs to someone else");
        }
    }

    @Transactional
    public Incident changeStatus(Long incidentId, AppUser actor, IncidentStatus next) {
        Incident incident = require(incidentId);
        IncidentStatus current = incident.getStatus();
        if (current == next) return incident;
        if (!current.canMoveTo(next)) {
            throw new IllegalStateException("Cannot move an incident from %s to %s".formatted(current, next));
        }
        incident.setStatus(next);
        log(incident, actor, "Status changed from %s to %s".formatted(current, next), next);
        return incidents.save(incident);
    }

    @Transactional
    public Incident assign(Long incidentId, AppUser actor, Long teamId) {
        Incident incident = require(incidentId);
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("No such team: " + teamId));
        incident.setAssignedTeam(team);
        incident.setNeedsTriage(false);
        if (incident.getStatus() == IncidentStatus.NEW) {
            incident.setStatus(IncidentStatus.ASSIGNED);
        }
        log(incident, actor, "Assigned to " + team.getName(), incident.getStatus());
        return incidents.save(incident);
    }

    @Transactional
    public Incident resolve(Long incidentId, AppUser actor, String resolutionText) {
        Incident incident = require(incidentId);
        if (!incident.getStatus().canMoveTo(IncidentStatus.RESOLVED)) {
            throw new IllegalStateException("Cannot resolve an incident that is " + incident.getStatus());
        }
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());
        incident.setResolvedBy(actor);
        incident.setResolutionText(resolutionText.trim());
        log(incident, actor, "Resolved: " + resolutionText.trim(), IncidentStatus.RESOLVED);
        return incidents.save(incident);
    }

    /**
     * Returns DTOs rather than entities: the mapping touches lazy associations, so it has
     * to happen while the transaction is still open.
     */
    @Transactional(readOnly = true)
    public Page<Dtos.IncidentSummary> queue(IncidentStatus status, String category, String q,
                                            boolean openOnly, Long createdBy, Pageable pageable) {
        Specification<Incident> spec = Specification
                .where(IncidentSpecs.hasStatus(status))
                .and(IncidentSpecs.hasCategory(category))
                .and(IncidentSpecs.matches(q))
                .and(IncidentSpecs.openOnly(openOnly))
                .and(IncidentSpecs.createdBy(createdBy));
        return incidents.findAll(spec, pageable).map(Dtos.IncidentSummary::from);
    }

    @Transactional(readOnly = true)
    public Dtos.IncidentDetail detailFor(Long incidentId, AppUser user) {
        requireVisibleTo(incidentId, user);
        return detail(incidentId);
    }

    @Transactional(readOnly = true)
    public Dtos.IncidentDetail detail(Long incidentId) {
        Incident i = require(incidentId);
        List<Dtos.ActivityEntry> activity =
                updates.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                        .map(Dtos.ActivityEntry::from)
                        .toList();

        return new Dtos.IncidentDetail(
                Dtos.IncidentSummary.from(i),
                i.getDescription(),
                i.getImpact(),
                i.getUrgency(),
                i.getSubcategory(),
                i.getCategoryConfidence(),
                PriorityMatrix.slaTargetHours(i.getPriority()),
                i.getResolvedAt(),
                i.getResolutionText(),
                i.actualResolutionHours(),
                readJson(i.getMlSimilarJson(), new TypeReference<List<MlContract.SimilarIncident>>() {}),
                readJson(i.getMlStepsJson(), new TypeReference<List<String>>() {}),
                activity,
                new ArrayList<>(i.getStatus().allowedNext()));
    }

    public Incident require(Long id) {
        return incidents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such incident: " + id));
    }

    private void log(Incident incident, AppUser user, String comment, IncidentStatus statusAfter) {
        updates.save(new IncidentUpdate(incident, user, comment, statusAfter, user == null));
    }

    /** Human-facing incident key. Public so the demo seeder produces identical references. */
    public static String reference(Long id) {
        return "INC-%06d".formatted(id);
    }

    private <T> void writeJson(T value, java.util.function.Consumer<String> setter) {
        if (value == null) return;
        try {
            setter.accept(json.writeValueAsString(value));
        } catch (Exception e) {
            log.warn("Could not serialise ML payload: {}", e.getMessage());
        }
    }

    private <T> T readJson(String raw, TypeReference<T> type) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return json.readValue(raw, type);
        } catch (Exception e) {
            log.warn("Could not read stored ML payload: {}", e.getMessage());
            return null;
        }
    }
}
