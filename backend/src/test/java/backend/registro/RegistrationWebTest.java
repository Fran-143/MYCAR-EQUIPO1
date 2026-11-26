package backend.registro;

import backend.registro.web.RegistrationController;
import backend.registro.web.RegistrationErrorHandler;
import backend.registro.web.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationWebTest {
    @Test void directRegistroRequiresVerification() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new RegistrationController())
                .setControllerAdvice(new RegistrationErrorHandler()).build();
        mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content("{}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("REGISTRO_REQUIERE_VERIFICACION"));
    }

    @Test void requestToStringDoesNotRevealPassword() {
        var request = new RegistrationRequest(null, null, null, null, "never-log-this", "never-log-this", null);
        assertFalse(request.toString().contains("never-log-this"));
    }
}
