package backend.autenticacion;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RF02 — TDD de validación de login (Arrange / Act / Assert). */
class LoginValidatorTest {
    static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    final LoginValidator validator=new LoginValidator(FACTORY.getValidator());
    @AfterAll static void close(){FACTORY.close();}
    @Test void normalizesOnlyEmailAndDoesNotReapplyRegistrationComplexity(){
        // Arrange
        var entrada = new LoginCommand("  Elsa.Test+Alias@EXAMPLE.COM  "," incorrecta ",UUID.randomUUID());

        // Act
        var command=validator.validate(entrada);

        // Assert
        assertEquals("elsa.test+alias@example.com",command.correo());
        assertEquals(" incorrecta ",command.contrasena());
        assertFalse(command.toString().contains("incorrecta"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"sin-arroba","@example.com","a@"," ","a b@example.com"})
    void invalidEmailIsRejectedBeforeAuthentication(String email){
        // Arrange: email inválido parametrizado

        // Act + Assert
        assertThrows(AuthenticationFailure.class,()->validator.validate(new LoginCommand(email,"x",UUID.randomUUID())));
    }
    @Test void missingFieldsAndNullCommandAreRejected(){
        // Arrange + Act + Assert
        assertEquals(3,assertThrows(AuthenticationFailure.class,()->validator.validate(new LoginCommand(null,null,null))).fields().size());
        assertThrows(AuthenticationFailure.class,()->validator.validate(null));
    }
}
