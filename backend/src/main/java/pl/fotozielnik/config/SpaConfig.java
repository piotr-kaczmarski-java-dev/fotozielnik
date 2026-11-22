package pl.fotozielnik.config;

import java.io.IOException;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built frontend in production (ADR-0006: one deployable; the Docker build copies {@code dist/}
 * to {@code classpath:/static/}). Client-side routes such as {@code /photos/123} get {@code index.html}.
 * In dev there is no static folder and Vite serves the frontend.
 */
@Configuration(proxyBeanMethods = false)
class SpaConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a content hash into every asset file name: cache them for a year.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC + "assets/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        // Everything else: the file if it exists, else the SPA shell. Never cached, so deploys show up at once.
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new SpaFallbackResolver());
    }

    static final class SpaFallbackResolver extends PathResourceResolver {
        @Override
        protected @Nullable Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            // unknown API/actuator URLs stay 404s instead of returning HTML; real files with an extension too
            if (resourcePath.startsWith("api/")
                    || resourcePath.startsWith("actuator/")
                    || resourcePath.startsWith("v3/")
                    || resourcePath.contains(".")) {
                return null;
            }
            Resource index = new ClassPathResource("static/index.html");
            return index.exists() ? index : null;
        }
    }
}
