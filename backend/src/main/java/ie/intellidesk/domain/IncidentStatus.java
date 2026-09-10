package ie.intellidesk.domain;

import java.util.EnumSet;
import java.util.Set;

/** Incident lifecycle. Transitions are enforced, not just recorded. */
public enum IncidentStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    RESOLVED,
    CLOSED;

    public Set<IncidentStatus> allowedNext() {
        return switch (this) {
            case NEW -> EnumSet.of(ASSIGNED, IN_PROGRESS, RESOLVED);
            case ASSIGNED -> EnumSet.of(IN_PROGRESS, ON_HOLD, RESOLVED);
            case IN_PROGRESS -> EnumSet.of(ON_HOLD, RESOLVED, ASSIGNED);
            case ON_HOLD -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED, IN_PROGRESS);
            case CLOSED -> EnumSet.noneOf(IncidentStatus.class);
        };
    }

    public boolean canMoveTo(IncidentStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED;
    }
}
