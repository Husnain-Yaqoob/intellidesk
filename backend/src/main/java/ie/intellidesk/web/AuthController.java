package ie.intellidesk.web;

import ie.intellidesk.web.dto.Dtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CurrentUserResolver currentUser;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, CurrentUserResolver currentUser) {
        this.authenticationManager = authenticationManager;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public Dtos.CurrentUser login(@Valid @RequestBody Dtos.LoginRequest request,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);

        return Dtos.CurrentUser.from(currentUser.require(authentication));
    }

    @GetMapping("/me")
    public Dtos.CurrentUser me(Authentication authentication) {
        return Dtos.CurrentUser.from(currentUser.require(authentication));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** The SPA calls this once on load so the CSRF cookie is issued before any POST. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
