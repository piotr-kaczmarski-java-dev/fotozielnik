package pl.fotozielnik.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pl.fotozielnik.config.SecurityConfig;

/** Web slice test: no database, no Docker - the default kind of test for controllers. */
@WebMvcTest(InfoController.class)
@Import(SecurityConfig.class)
class InfoControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void infoIsPublic() throws Exception {
        mvc.perform(get("/api/public/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("fotozielnik"));
    }
}
