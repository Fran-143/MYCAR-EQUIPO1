package backend.registro;

import backend.registro.web.RegistrationController;
import backend.registro.web.RegistrationErrorHandler;
import backend.registro.web.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationWebTest {
    @Test void unexpectedFailuresAreSanitized() throws Exception {
        assertSafeFailure(new IllegalStateException("internal-sensitive-details"),500,"ERROR_INTERNO");
    }

    @Test void databaseOutageIsUnavailableNotInvalidCredentials() throws Exception {
        assertSafeFailure(new DataAccessResourceFailureException("internal-sensitive-details"),503,"REGISTRO_NO_DISPONIBLE");
    }

    @Test void requestToStringDoesNotRevealPassword() {
        var request=new RegistrationRequest(null,null,null,null,"never-log-this","never-log-this",null);
        assertFalse(request.toString().contains("never-log-this"));
    }

    private void assertSafeFailure(RuntimeException exception,int status,String code) throws Exception {
        var useCase=mock(RegisterAccount.class);
        when(useCase.register(any())).thenThrow(exception);
        var mvc=MockMvcBuilders.standaloneSetup(new RegistrationController(useCase))
                .setControllerAdvice(new RegistrationErrorHandler()).build();
        String response=mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content("{}"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.codigo").value(code))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("internal-sensitive-details"));
    }
}
