package ie.intellidesk.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** One line of the incident's activity trail. Append-only. */
@Entity
@Table(name = "incident_updates")
public class IncidentUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", length = 16)
    private IncidentStatus statusAfter;

    /** True for entries the system wrote (classification, assignment, transitions). */
    @Column(nullable = false)
    private boolean systemGenerated = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IncidentUpdate() {
    }

    public IncidentUpdate(Incident incident, AppUser user, String comment,
                          IncidentStatus statusAfter, boolean systemGenerated) {
        this.incident = incident;
        this.user = user;
        this.comment = comment;
        this.statusAfter = statusAfter;
        this.systemGenerated = systemGenerated;
    }

    public Long getId() { return id; }
    public Incident getIncident() { return incident; }
    public AppUser getUser() { return user; }
    public String getComment() { return comment; }
    public IncidentStatus getStatusAfter() { return statusAfter; }
    public boolean isSystemGenerated() { return systemGenerated; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
