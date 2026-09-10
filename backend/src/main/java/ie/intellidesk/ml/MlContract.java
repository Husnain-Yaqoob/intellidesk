package ie.intellidesk.ml;

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
}
