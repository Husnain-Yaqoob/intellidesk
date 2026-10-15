package ie.intellidesk.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The wire contract between the Java application and the Python ML service.
 *
 * <p>Kept in one file on purpose: this is the seam the whole project is designed
 * around, and both sides have to agree on it exactly. The Python service mirrors
 * these shapes in ml-service/schemas.py.
 */
public final class MlContract {

    private MlContract() {
    }

    public record AnalyseRequest(
            String title,
            String description,
            String impact,
            String urgency) {
    }

    public record SimilarIncident(
            String reference,
            String title,
            String resolution,
            double similarity) {
    }

    public record AnalyseResponse(
            String category,
            String subcategory,
            double confidence,
            @JsonProperty("predicted_resolution_hours") Double predictedResolutionHours,
            @JsonProperty("sla_breach_risk") Double slaBreachRisk,
            @JsonProperty("similar_incidents") List<SimilarIncident> similarIncidents,
            @JsonProperty("suggested_steps") List<String> suggestedSteps,
            @JsonProperty("model_version") String modelVersion) {
    }

    // ---------------------------------------------------------------------------
    // LLM drafting — mirrors DraftRequest / DraftResponse in ml-service/schemas.py.
    // ---------------------------------------------------------------------------

    public record DraftRequest(
            String title,
            String description,
            String category) {
    }

    public record DraftStep(
            String action,
            String source,
            @JsonProperty("source_title") String sourceTitle) {
    }

    public record DraftSource(
            String reference,
            String title,
            String resolution,
            double similarity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftGuards(
            @JsonProperty("injection_suspected") boolean injectionSuspected,
            @JsonProperty("injection_signals") List<String> injectionSignals,
            int redactions,
            @JsonProperty("rejected_steps") int rejectedSteps,
            @JsonProperty("links_removed") int linksRemoved,
            @JsonProperty("reply_withheld") boolean replyWithheld) {
    }

    public record DraftToolCall(String name, String query, int results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftTrace(
            String model,
            int rounds,
            @JsonProperty("tool_calls") List<DraftToolCall> toolCalls,
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("latency_ms") long latencyMs) {
    }

    /** status is "drafted" or "abstained". An abstention is a valid answer, not an error. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DraftResponse(
            String status,
            String summary,
            List<DraftStep> steps,
            @JsonProperty("reply_to_user") String replyToUser,
            String confidence,
            @JsonProperty("abstain_reason") String abstainReason,
            List<DraftSource> sources,
            DraftGuards guards,
            DraftTrace trace,
            @JsonProperty("draft_version") String draftVersion) {
    }
}
