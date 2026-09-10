package ie.intellidesk.web;

import ie.intellidesk.domain.AppUser;
import ie.intellidesk.domain.IncidentStatus;
import ie.intellidesk.domain.Role;
import ie.intellidesk.repo.TeamRepository;
import ie.intellidesk.service.IncidentService;
import ie.intellidesk.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class IncidentController {

    private final IncidentService service;
    private final CurrentUserResolver currentUser;
    private final TeamRepository teams;

    public IncidentController(IncidentService service, CurrentUserResolver currentUser, TeamRepository teams) {
        this.service = service;
        this.currentUser = currentUser;
        this.teams = teams;
    }

    @PostMapping("/incidents")
    public Dtos.IncidentDetail create(@Valid @RequestBody Dtos.CreateIncidentRequest request,
                                      Authentication authentication) {
        AppUser user = currentUser.require(authentication);
        var incident = service.create(request, user);
        return service.detail(incident.getId());
    }

    /**
     * Employees see only their own incidents. Enforced here rather than by having a
     * separate "my incidents" endpoint, so there is no wider endpoint to call instead.
     */
    @GetMapping("/incidents")
    public Page<Dtos.IncidentSummary> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Authentication authentication) {

        AppUser user = currentUser.require(authentication);
        Long restrictTo = user.getRole() == Role.AGENT ? null : user.getId();

        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.ASC, "priority").and(Sort.by(Sort.Direction.DESC, "createdAt")));

        return service.queue(status, category, q, openOnly, restrictTo, pageable);
    }

    @GetMapping("/incidents/{id}")
    public Dtos.IncidentDetail detail(@PathVariable Long id, Authentication authentication) {
        return service.detailFor(id, currentUser.require(authentication));
    }

    @PostMapping("/incidents/{id}/notes")
    public Dtos.IncidentDetail addNote(@PathVariable Long id,
                                       @Valid @RequestBody Dtos.AddNoteRequest request,
                                       Authentication authentication) {
        AppUser user = currentUser.require(authentication);
        service.addNote(id, user, request.comment());
        return service.detail(id);
    }

    @PostMapping("/incidents/{id}/status")
    public Dtos.IncidentDetail changeStatus(@PathVariable Long id,
                                            @Valid @RequestBody Dtos.StatusChangeRequest request,
                                            Authentication authentication) {
        service.changeStatus(id, currentUser.require(authentication), request.status());
        return service.detail(id);
    }

    @PostMapping("/incidents/{id}/assign")
    public Dtos.IncidentDetail assign(@PathVariable Long id,
                                      @Valid @RequestBody Dtos.AssignRequest request,
                                      Authentication authentication) {
        service.assign(id, currentUser.require(authentication), request.teamId());
        return service.detail(id);
    }

    @PostMapping("/incidents/{id}/resolve")
    public Dtos.IncidentDetail resolve(@PathVariable Long id,
                                       @Valid @RequestBody Dtos.ResolveRequest request,
                                       Authentication authentication) {
        service.resolve(id, currentUser.require(authentication), request.resolutionText());
        return service.detail(id);
    }

    @GetMapping("/teams")
    public List<Dtos.TeamView> teams() {
        return teams.findAll().stream().map(Dtos.TeamView::from).toList();
    }
}
