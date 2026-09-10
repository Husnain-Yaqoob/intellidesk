package ie.intellidesk.service;

import ie.intellidesk.domain.*;
import ie.intellidesk.repo.IncidentUpdateRepository;
import ie.intellidesk.repo.TeamRepository;
import ie.intellidesk.repo.UserRepository;
import ie.intellidesk.web.dto.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncidentServiceTest {

    @Autowired IncidentService service;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired IncidentUpdateRepository updates;

    private AppUser employee;
    private AppUser agent;

    @BeforeEach
    void setUp() {
        employee = users.save(new AppUser("Test Employee", "emp-" + System.nanoTime() + "@test.ie",
                "{noop}x", Role.EMPLOYEE));
        agent = users.save(new AppUser("Test Agent", "agent-" + System.nanoTime() + "@test.ie",
                "{noop}x", Role.AGENT));
    }

    private Incident create(Impact impact, Urgency urgency) {
        return service.create(new Dtos.CreateIncidentRequest(
                "VPN disconnecting repeatedly",
                "My VPN drops every ten minutes when working from home.",
                impact, urgency), employee);
    }

    @Test
    void derivesPriorityFromImpactAndUrgency() {
        assertThat(create(Impact.HIGH, Urgency.HIGH).getPriority()).isEqualTo(Priority.P1);
        assertThat(create(Impact.LOW, Urgency.LOW).getPriority()).isEqualTo(Priority.P4);
    }

    @Test
    void assignsAHumanReadableReference() {
        Incident incident = create(Impact.MEDIUM, Urgency.MEDIUM);
        assertThat(incident.getReference()).matches("INC-\\d{6}");
    }

    /**
     * The behaviour that matters most: with the ML service switched off the incident is
     * still created. It is flagged for manual triage rather than lost.
     */
    @Test
    void stillLogsTheIncidentWhenTheModelServiceIsUnavailable() {
        Incident incident = create(Impact.HIGH, Urgency.MEDIUM);

        assertThat(incident.getId()).isNotNull();
        assertThat(incident.isNeedsTriage()).isTrue();
        assertThat(incident.getCategory()).isNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    @Test
    void writesAnActivityTrailFromTheMomentItIsRaised() {
        Incident incident = create(Impact.HIGH, Urgency.HIGH);

        var trail = updates.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        assertThat(trail).isNotEmpty();
        assertThat(trail.get(0).getComment()).contains("Incident created with priority P1");
        assertThat(trail).allMatch(IncidentUpdate::isSystemGenerated);
    }

    @Test
    void resolvingRecordsWhoWhenAndHow() {
        Incident incident = create(Impact.MEDIUM, Urgency.HIGH);

        Incident resolved = service.resolve(incident.getId(), agent, "Reissued the VPN certificate.");

        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolved.getResolvedBy().getId()).isEqualTo(agent.getId());
        assertThat(resolved.getResolutionText()).isEqualTo("Reissued the VPN certificate.");
        assertThat(resolved.actualResolutionHours()).isNotNull().isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void refusesAnIllegalStatusTransition() {
        Incident incident = create(Impact.LOW, Urgency.LOW);

        assertThatThrownBy(() -> service.changeStatus(incident.getId(), agent, IncidentStatus.CLOSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot move an incident from NEW to CLOSED");
    }

    @Test
    void assigningClearsTheTriageFlag() {
        Team team = teams.save(new Team("Network Support " + System.nanoTime(), "Network"));
        Incident incident = create(Impact.HIGH, Urgency.HIGH);
        assertThat(incident.isNeedsTriage()).isTrue();

        Incident assigned = service.assign(incident.getId(), agent, team.getId());

        assertThat(assigned.isNeedsTriage()).isFalse();
        assertThat(assigned.getAssignedTeam().getId()).isEqualTo(team.getId());
        assertThat(assigned.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
    }

    @Test
    void detailExposesTheTransitionsTheUiIsAllowedToOffer() {
        Incident incident = create(Impact.MEDIUM, Urgency.MEDIUM);

        Dtos.IncidentDetail detail = service.detail(incident.getId());

        assertThat(detail.allowedTransitions()).contains(IncidentStatus.ASSIGNED, IncidentStatus.RESOLVED);
        assertThat(detail.allowedTransitions()).doesNotContain(IncidentStatus.CLOSED);
        assertThat(detail.slaTargetHours()).isPositive();
    }
}
