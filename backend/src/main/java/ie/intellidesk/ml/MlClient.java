package ie.intellidesk.ml;

import ie.intellidesk.domain.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

/**
 * Calls the Python ML service.
 *
 * <p>Every method degrades to {@link Optional#empty()} rather than throwing. An
 * incident must always be loggable: if the model service is down, the incident is
 * still created, flagged for manual triage, and the agent carries on. A service desk
 * that stops accepting tickets because a model is unavailable is worse than one with
 * no model at all.
 */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);

    private final RestClient client;
    private final boolean enabled;

    public MlClient(@Value("${intellidesk.ml.base-url:http://localhost:8000}") String baseUrl,
                    @Value("${intellidesk.ml.enabled:true}") boolean enabled,
                    @Value("${intellidesk.ml.timeout-ms:2000}") int timeoutMs) {
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public Optional<MlContract.AnalyseResponse> analyse(Incident incident) {
        if (!enabled) return Optional.empty();
        var request = new MlContract.AnalyseRequest(
                incident.getTitle(),
                incident.getDescription(),
                incident.getImpact().name(),
                incident.getUrgency().name());
        try {
            var response = client.post()
                    .uri("/analyse")
                    .body(request)
                    .retrieve()
                    .body(MlContract.AnalyseResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception e) {
            // Expected whenever the ML service is not running. Not an error for the caller.
            log.warn("ML service unavailable ({}); incident will be flagged for manual triage",
                    e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
