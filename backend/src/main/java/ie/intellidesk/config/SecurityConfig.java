package ie.intellidesk.config;

import ie.intellidesk.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
public class SecurityConfig {

    /**
     * Session cookies rather than JWTs. The SPA is first-party and served from the same
     * origin in production, sessions can be revoked server-side the moment an agent leaves,
     * and there is no token to leak into browser storage. JWT would be the right answer for
     * third-party API clients — there aren't any here.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource cors) throws Exception {
        // Cookie-based CSRF tokens, readable by JavaScript so the SPA can echo them back.
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .cors(c -> c.configurationSource(cors))
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/login", "/api/auth/csrf", "/api/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/dashboard").hasRole("AGENT")
                    .requestMatchers("/api/teams").hasRole("AGENT")
                    .requestMatchers(HttpMethod.POST, "/api/incidents/*/assign").hasRole("AGENT")
                    .requestMatchers(HttpMethod.POST, "/api/incidents/*/status").hasRole("AGENT")
                    .requestMatchers(HttpMethod.POST, "/api/incidents/*/resolve").hasRole("AGENT")
                    .anyRequest().authenticated())
            // Return 401 instead of redirecting to a login page the SPA doesn't have.
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))
            .logout(l -> l.disable());

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${intellidesk.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
