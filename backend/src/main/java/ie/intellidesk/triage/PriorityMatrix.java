package ie.intellidesk.triage;

import ie.intellidesk.domain.Impact;
import ie.intellidesk.domain.Priority;
import ie.intellidesk.domain.Urgency;

/**
 * Impact x Urgency -> Priority.
 *
 * <p>This is a deliberate design decision, not a shortcut. Priority drives who gets
 * shouted at and in what order, so it has to be predictable and defensible to the
 * person whose incident was ranked below someone else's. A model that is 94% accurate
 * is worse here than a rule that is 100% explainable.
 *
 * <pre>
 *              URGENCY
 *            High  Med   Low
 *   I  High   P1    P2    P3
 *   M  Med    P2    P3    P4
 *   P  Low    P3    P4    P4
 * </pre>
 */
public final class PriorityMatrix {

    private PriorityMatrix() {
    }

    public static Priority resolve(Impact impact, Urgency urgency) {
        if (impact == null || urgency == null) {
            throw new IllegalArgumentException("impact and urgency are both required");
        }
        return switch (impact) {
            case HIGH -> switch (urgency) {
                case HIGH -> Priority.P1;
                case MEDIUM -> Priority.P2;
                case LOW -> Priority.P3;
            };
            case MEDIUM -> switch (urgency) {
                case HIGH -> Priority.P2;
                case MEDIUM -> Priority.P3;
                case LOW -> Priority.P4;
            };
            case LOW -> switch (urgency) {
                case HIGH -> Priority.P3;
                case MEDIUM, LOW -> Priority.P4;
            };
        };
    }

    /** Target resolution time in hours, used later for SLA breach flagging. */
    public static int slaTargetHours(Priority priority) {
        return switch (priority) {
            case P1 -> 4;
            case P2 -> 8;
            case P3 -> 24;
            case P4 -> 72;
        };
    }
}
