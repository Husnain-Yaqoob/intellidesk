package ie.intellidesk.service;

import ie.intellidesk.domain.AppUser;
import ie.intellidesk.domain.Impact;
import ie.intellidesk.domain.Incident;
import ie.intellidesk.domain.Role;
import ie.intellidesk.domain.Urgency;
import ie.intellidesk.ml.MlClient;
import ie.intellidesk.ml.MlContract;
import ie.intellidesk.repo.UserRepository;
import ie.intellidesk.web.dto.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What the application does with a classification it does not trust.
 *
 * <p>The rule being pinned down here: a predicted resolution time and an SLA risk are
 * both derived from the predicted category. If the category was too uncertain to route
 * on, those numbers rest on a guess the application explicitly refused to make, and
 * showing them anyway invites an agent to believe a figure with nothing behind it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LowConfidenceTriageTest {

    @MockBean
    MlClient ml;

    @Autowired
    IncidentService service;

    @Autowired
    UserRepository users;

    private AppUser employee;

    @BeforeEach
    void setUp() {
        employee = users.save(new AppUser("Triage Tester", "triage-" + System.nanoTime() + "@test.ie",
                "{noop}x", Role.EMPLOYEE));
    }

    private MlContract.AnalyseResponse response(double confidence) {
        return new MlContract.AnalyseResponse(
                "Software", "Outlook", confidence, 9.4, 0.82,
                List.of(new MlContract.SimilarIncident(
                        "INC-100121", "Outlook closes itself", "Repaired the installation.", 0.81)),
                List.of("Repaired the installation."),
                "test-model");
    }

    private Incident raise() {
        return service.create(new Dtos.CreateIncidentRequest(
                "Something is wrong", "It is not working properly today.",
                Impact.MEDIUM, Urgency.MEDIUM), employee);
    }

    @Test
    void belowTheThresholdNothingDerivedFromTheCategoryIsKept() {
        when(ml.analyse(any())).thenReturn(Optional.of(response(0.31)));

        Incident incident = raise();

        assertThat(incident.isNeedsTriage()).isTrue();
        assertThat(incident.getCategory()).isNull();
        assertThat(incident.getAssignedTeam()).isNull();
        assertThat(incident.getPredictedResolutionHours()).isNull();
        assertThat(incident.getSlaBreachRisk()).isNull();

        // The confidence itself is still recorded — that is evidence about the model,
        // not a claim about the incident.
        assertThat(incident.getCategoryConfidence()).isEqualTo(0.31);
    }

    @Test
    void aboveTheThresholdThePredictionIsKept() {
        when(ml.analyse(any())).thenReturn(Optional.of(response(0.94)));

        Incident incident = raise();

        assertThat(incident.isNeedsTriage()).isFalse();
        assertThat(incident.getCategory()).isEqualTo("Software");
        assertThat(incident.getPredictedResolutionHours()).isEqualTo(9.4);
        assertThat(incident.getSlaBreachRisk()).isEqualTo(0.82);
    }

    @Test
    void noSuggestionsAreOfferedForAnIncidentTheModelCouldNotClassify() {
        when(ml.analyse(any())).thenReturn(Optional.of(response(0.31)));

        Incident incident = raise();
        Dtos.IncidentDetail detail = service.detail(incident.getId());

        // Vague text defeats the similarity search for the same reason it defeats the
        // classifier, so its neighbours are noise however good they look. Offering
        // resolution steps drawn from them is worse than offering nothing.
        assertThat(detail.similarIncidents()).isNull();
        assertThat(detail.suggestedSteps()).isNull();
    }

    @Test
    void suggestionsAreOfferedOnceTheCategoryIsTrusted() {
        when(ml.analyse(any())).thenReturn(Optional.of(response(0.94)));

        Incident incident = raise();
        Dtos.IncidentDetail detail = service.detail(incident.getId());

        assertThat(detail.similarIncidents()).hasSize(1);
        assertThat(detail.suggestedSteps()).containsExactly("Repaired the installation.");
    }
}
