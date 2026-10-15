package ie.intellidesk.service;

import ie.intellidesk.domain.AppUser;
import ie.intellidesk.ml.MlClient;
import ie.intellidesk.ml.MlContract;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates an AI-assisted draft for one incident.
 *
 * <p>Deliberately not transactional itself. The model call can take tens of seconds on
 * a local model, and holding a database connection open for that long is how a demo
 * app runs out of connections. So the read and the write each get their own short
 * transaction in {@link IncidentService}, and the slow HTTP call sits between them.
 */
@Service
public class AiDraftService {

    private final IncidentService incidents;
    private final MlClient ml;

    public AiDraftService(IncidentService incidents, MlClient ml) {
        this.incidents = incidents;
        this.ml = ml;
    }

    /** @return false when no draft could be produced. The attempt is logged either way. */
    public boolean draft(Long incidentId, AppUser agent) {
        IncidentService.DraftInput input = incidents.draftInput(incidentId);
        Optional<MlContract.DraftResponse> result = ml.draft(input.title(), input.description(), input.category());
        incidents.recordAiDraft(incidentId, agent, result.orElse(null));
        return result.isPresent();
    }

    /** Mapped to 503 by the exception handler. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException() {
            super("AI drafting is unavailable — the language model is not running or did not respond");
        }
    }
}
