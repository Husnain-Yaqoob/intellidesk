package ie.intellidesk.domain;

/**
 * Derived from impact and urgency by {@link ie.intellidesk.triage.PriorityMatrix}.
 * Deliberately a business rule rather than a model: it must be explainable to
 * the person whose ticket got deprioritised.
 */
public enum Priority {
    P1("Critical"),
    P2("High"),
    P3("Medium"),
    P4("Low");

    private final String label;

    Priority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
