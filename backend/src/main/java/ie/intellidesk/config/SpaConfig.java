package ie.intellidesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React app when it is present on the classpath.
 *
 * <p>The hosted build compiles the front end into {@code /static} inside the jar (see the
 * Dockerfile at the repository root), so one service answers both the UI and the API from
 * a single origin. Locally nothing here does anything — the directory is empty and nginx
 * serves the SPA instead.
 *
 * <p>The part that needs saying: React Router owns the URLs. A browser asking for
 * {@code /incidents/141} is not asking for a file, it is asking for the app, which will
 * then render that route on the client. Without the fallback below, Spring returns 404 for
 * every URL except the root, and the app appears to work until someone refreshes the page.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";
    private static final ClassPathResource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {

                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // API paths must never fall through to index.html. A missing endpoint
                        // should answer 404, not hand the caller a page of HTML — that turns a
                        // clear error into a JSON parse failure three layers away.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }

                        return INDEX.exists() ? INDEX : null;
                    }
                });
    }
}
