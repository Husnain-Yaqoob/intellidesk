package ie.intellidesk.triage;

import ie.intellidesk.domain.Impact;
import ie.intellidesk.domain.Priority;
import ie.intellidesk.domain.Urgency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriorityMatrixTest {

    @ParameterizedTest(name = "impact {0} x urgency {1} -> {2}")
    @CsvSource({
            "HIGH,   HIGH,   P1",
            "HIGH,   MEDIUM, P2",
            "HIGH,   LOW,    P3",
            "MEDIUM, HIGH,   P2",
            "MEDIUM, MEDIUM, P3",
            "MEDIUM, LOW,    P4",
            "LOW,    HIGH,   P3",
            "LOW,    MEDIUM, P4",
            "LOW,    LOW,    P4"
    })
    void resolvesEveryCombination(Impact impact, Urgency urgency, Priority expected) {
        assertThat(PriorityMatrix.resolve(impact, urgency)).isEqualTo(expected);
    }

    @Test
    void everyCombinationIsCovered() {
        for (Impact impact : Impact.values()) {
            for (Urgency urgency : Urgency.values()) {
                assertThat(PriorityMatrix.resolve(impact, urgency)).isNotNull();
            }
        }
    }

    @Test
    void rejectsMissingInputs() {
        assertThatThrownBy(() -> PriorityMatrix.resolve(null, Urgency.HIGH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PriorityMatrix.resolve(Impact.HIGH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slaTargetsTightenWithPriority() {
        assertThat(PriorityMatrix.slaTargetHours(Priority.P1))
                .isLessThan(PriorityMatrix.slaTargetHours(Priority.P2));
        assertThat(PriorityMatrix.slaTargetHours(Priority.P2))
                .isLessThan(PriorityMatrix.slaTargetHours(Priority.P3));
        assertThat(PriorityMatrix.slaTargetHours(Priority.P3))
                .isLessThan(PriorityMatrix.slaTargetHours(Priority.P4));
    }
}
