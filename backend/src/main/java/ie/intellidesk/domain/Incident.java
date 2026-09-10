package ie.intellidesk.domain;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_status", columnList = "status"),
        @Index(name = "idx_incident_created", columnList = "created_at")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-facing key, e.g. INC-000128. Assigned once the row has an id. */
    @Column(unique = true, length = 16)
    private String reference;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private Impact impact;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private Urgency urgency;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 4)
    private Priority priority;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private IncidentStatus status = IncidentStatus.NEW;

    // ---- filled by the ML service; all nullable so the app works without it ----

    @Column(length = 40)
    private String category;

    @Column(length = 40)
    private String subcategory;

    /** Classifier confidence, 0..1. Below the threshold the incident is flagged for manual triage. */
    private Double categoryConfidence;

    /** True when the classifier was not confident enough to be trusted. */
    @Column(nullable = false)
    private boolean needsTriage = false;

    private Double predictedResolutionHours;

    /** Probability the incident breaches its SLA target, 0..1. */
    private Double slaBreachRisk;

    /**
     * What the model said at triage time, stored as JSON rather than re-queried.
     * The agent needs to see the advice the incident was actually triaged on, not
     * whatever a retrained model would say today — and it keeps the audit honest.
     */
    @Column(name = "ml_similar_json", columnDefinition = "text")
    private String mlSimilarJson;

    @Column(name = "ml_steps_json", columnDefinition = "text")
    private String mlStepsJson;

    @Column(name = "ml_model_version", length = 40)
    private String mlModelVersion;

    // ---------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_team_id")
    private Team assignedTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_text", columnDefinition = "text")
    private String resolutionText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private AppUser resolvedBy;

    protected Incident() {
    }

    public Incident(String title, String description, Impact impact, Urgency urgency,
                    Priority priority, AppUser createdBy) {
        this.title = title;
        this.description = description;
        this.impact = impact;
        this.urgency = urgency;
        this.priority = priority;
        this.createdBy = createdBy;
    }

    /** Actual time to resolve, once resolved. Empty while still open. */
    public Double actualResolutionHours() {
        if (resolvedAt == null) return null;
        return Duration.between(createdAt, resolvedAt).toMinutes() / 60.0;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public Impact getImpact() { return impact; }
    public void setImpact(Impact impact) { this.impact = impact; }
    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public Double getCategoryConfidence() { return categoryConfidence; }
    public void setCategoryConfidence(Double c) { this.categoryConfidence = c; }
    public boolean isNeedsTriage() { return needsTriage; }
    public void setNeedsTriage(boolean needsTriage) { this.needsTriage = needsTriage; }
    public Double getPredictedResolutionHours() { return predictedResolutionHours; }
    public void setPredictedResolutionHours(Double h) { this.predictedResolutionHours = h; }
    public Double getSlaBreachRisk() { return slaBreachRisk; }
    public void setSlaBreachRisk(Double r) { this.slaBreachRisk = r; }
    public String getMlSimilarJson() { return mlSimilarJson; }
    public void setMlSimilarJson(String json) { this.mlSimilarJson = json; }
    public String getMlStepsJson() { return mlStepsJson; }
    public void setMlStepsJson(String json) { this.mlStepsJson = json; }
    public String getMlModelVersion() { return mlModelVersion; }
    public void setMlModelVersion(String v) { this.mlModelVersion = v; }
    public Team getAssignedTeam() { return assignedTeam; }
    public void setAssignedTeam(Team assignedTeam) { this.assignedTeam = assignedTeam; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionText() { return resolutionText; }
    public void setResolutionText(String resolutionText) { this.resolutionText = resolutionText; }
    public AppUser getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(AppUser resolvedBy) { this.resolvedBy = resolvedBy; }
}
