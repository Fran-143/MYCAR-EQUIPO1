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

class LoginValidatorTest {
    static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    final LoginValidator validator=new LoginValidator(FACTORY.getValidator());
    @AfterAll static void close(){FACTORY.close();}
    @Test void normalizesOnlyEmailAndDoesNotReapplyRegistrationComplexity(){
        var command=validator.validate(new LoginCommand("  Elsa.Test+Alias@EXAMPLE.COM  "," incorrecta ",UUID.randomUUID()));
        assertEquals("elsa.test+alias@example.com",command.correo());
        assertEquals(" incorrecta ",command.contrasena());
        assertFalse(command.toString().contains("incorrecta"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"sin-arroba","@example.com","a@"," ","a b@example.com"})
    void invalidEmailIsRejectedBeforeAuthentication(String email){
        assertThrows(AuthenticationFailure.class,()->validator.validate(new LoginCommand(email,"x",UUID.randomUUID())));
    }
    @Test void missingFieldsAndNullCommandAreRejected(){
        assertEquals(3,assertThrows(AuthenticationFailure.class,()->validator.validate(new LoginCommand(null,null,null))).fields().size());
        assertThrows(AuthenticationFailure.class,()->validator.validate(null));
    }
}
