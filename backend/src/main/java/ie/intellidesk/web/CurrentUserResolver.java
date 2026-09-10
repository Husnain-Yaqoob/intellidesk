package ie.intellidesk.web;

import ie.intellidesk.domain.AppUser;
import ie.intellidesk.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Turns the authenticated principal back into the domain user. */
@Component
public class CurrentUserResolver {

    private final UserRepository users;

    public CurrentUserResolver(UserRepository users) {
        this.users = users;
    }

    public AppUser require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated principal has no matching user row: " + authentication.getName()));
    }
}
