package pl.fotozielnik.system;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Smoke endpoint: proves backend, security config and frontend proxy are wired (used by the e2e smoke test). */
@RestController
@RequestMapping("/api/public/info")
class InfoController {

    private final AppInfo info;

    InfoController(Environment env, ObjectProvider<BuildProperties> build) {
        BuildProperties props = build.getIfAvailable();
        String version = props != null ? props.getVersion() : null;
        this.info = new AppInfo(env.getProperty("spring.application.name", "app"), version != null ? version : "dev");
    }

    @GetMapping
    AppInfo info() {
        return info;
    }
}
