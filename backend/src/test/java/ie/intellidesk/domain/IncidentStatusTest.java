package ie.intellidesk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentStatusTest {

    @Test
    void closedIsTerminal() {
        assertThat(IncidentStatus.CLOSED.allowedNext()).isEmpty();
        for (IncidentStatus next : IncidentStatus.values()) {
            assertThat(IncidentStatus.CLOSED.canMoveTo(next)).isFalse();
        }
    }

    @Test
    void aNewIncidentCannotJumpStraightToClosed() {
        assertThat(IncidentStatus.NEW.canMoveTo(IncidentStatus.CLOSED)).isFalse();
        assertThat(IncidentStatus.NEW.canMoveTo(IncidentStatus.ASSIGNED)).isTrue();
    }

    @Test
    void aResolvedIncidentCanBeReopened() {
        assertThat(IncidentStatus.RESOLVED.canMoveTo(IncidentStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    void openStatesAreTheOnesStillOnTheQueue() {
        assertThat(IncidentStatus.NEW.isOpen()).isTrue();
        assertThat(IncidentStatus.ASSIGNED.isOpen()).isTrue();
        assertThat(IncidentStatus.IN_PROGRESS.isOpen()).isTrue();
        assertThat(IncidentStatus.ON_HOLD.isOpen()).isTrue();
        assertThat(IncidentStatus.RESOLVED.isOpen()).isFalse();
        assertThat(IncidentStatus.CLOSED.isOpen()).isFalse();
    }

    @Test
    void noStatusAllowsATransitionToItself() {
        for (IncidentStatus status : IncidentStatus.values()) {
            assertThat(status.allowedNext()).doesNotContain(status);
        }
    }
}
