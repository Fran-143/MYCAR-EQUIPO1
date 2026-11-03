package backend.cierresesion;

import backend.autenticacion.AuthenticationFailure;
import backend.cierresesion.web.LogoutController;
import backend.cierresesion.web.LogoutErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** RF06 — TDD HTTP de logout (Arrange / Act / Assert). */
class LogoutWebTest {
    @Test void missingProofNeverInvokesCloseService()throws Exception{
        // Arrange
        var useCase=mock(CloseSession.class);
        var mvc=MockMvcBuilders.standaloneSetup(new LogoutController(useCase)).setControllerAdvice(new LogoutErrorHandler()).build();

        // Act
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());

        // Assert
        verifyNoInteractions(useCase);
    }
    @Test void databaseFailureIsSanitized503()throws Exception{safeFailure(new DataAccessResourceFailureException("private-fixture-details"),503);}
    @Test void unexpectedFailureIsSanitized500()throws Exception{safeFailure(new IllegalStateException("private-fixture-details"),500);}
    @Test void invalidProofIsSanitized401()throws Exception{safeFailure(AuthenticationFailure.session(),401);}
    private void safeFailure(RuntimeException failure,int expected)throws Exception{
        // Arrange
        var useCase=mock(CloseSession.class);
        doThrow(failure).when(useCase).close("fixture-proof");
        var mvc=MockMvcBuilders.standaloneSetup(new LogoutController(useCase)).setControllerAdvice(new LogoutErrorHandler()).build();

        // Act
        String response=mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer fixture-proof"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();

        // Assert
        assertFalse(response.contains("private-fixture-details"));assertFalse(response.contains("fixture-proof"));
    }
}
