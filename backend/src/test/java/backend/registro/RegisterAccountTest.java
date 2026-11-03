package backend.registro;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RF01 — TDD del caso de uso RegisterAccount (patrón Arrange / Act / Assert). */
class RegisterAccountTest {
    private final RegistrationValidator validator = mock(RegistrationValidator.class);
    private final RegistrationStore store = mock(RegistrationStore.class);
    private final PasswordEncoder encoder = new RegistrationConfiguration().registrationPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
    private final RegistrationCommand command = RegistrationValidatorTest.command("Ana", LocalDate.of(1995,1,1), "ClaveSegura1!");
    private final RegistrationData data = new RegistrationData("Ana","Pérez","ana@example.com",LocalDate.of(1995,1,1),"FIXTURE-NO-LEGAL");

    @Test void argon2idHasRandomSaltExplicitCostAndNoPasswordTruncation() {
        // Arrange
        String password = "Áb1!"+"ñ".repeat(60);

        // Act
        String a = encoder.encode(password), b = encoder.encode(password);

        // Assert
        assertTrue(a.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertNotEquals(a,b);
        assertTrue(a.length()<=255);
        assertTrue(encoder.matches(password,a));
        assertFalse(encoder.matches(password.substring(0,password.length()-1),a));
        assertFalse(encoder.matches(password.toLowerCase(),a));
    }

    @Test void hashesOnlyAfterValidationAndStoresServerTime() {
        // Arrange
        when(validator.validate(command)).thenReturn(data);
        when(store.createAccount(eq(data),anyString(),eq(clock.instant()))).thenAnswer(call -> {
            assertTrue(encoder.matches(command.contrasena(),call.getArgument(1)));
            return 41L;
        });

        // Act
        long id = new RegisterAccount(validator,new RegistrationPrivacyPolicy("FIXTURE-NO-LEGAL"),store,encoder,clock).register(command);

        // Assert
        assertEquals(41L, id);
        verify(store).emailExists(data.correo());
        verify(store).createAccount(eq(data),anyString(),eq(clock.instant()));
    }

    @Test void missingPrivacyVersionPreventsAllPersistence() {
        // Arrange
        when(validator.validate(command)).thenReturn(data);

        // Act + Assert
        assertThrows(RegistrationException.class, () -> new RegisterAccount(validator,new RegistrationPrivacyPolicy(""),store,encoder,clock).register(command));
        verifyNoInteractions(store);
    }

    @Test void existingEmailDoesNotCreateAnythingAndUsesGenericMessage() {
        // Arrange
        when(validator.validate(command)).thenReturn(data);
        when(store.emailExists(data.correo())).thenReturn(true);

        // Act
        var ex=assertThrows(RegistrationException.class, () -> new RegisterAccount(validator,new RegistrationPrivacyPolicy("FIXTURE-NO-LEGAL"),store,encoder,clock).register(command));

        // Assert
        assertEquals("Correo no disponible.",ex.getMessage());
        verify(store,never()).createAccount(any(),anyString(),any());
    }
}
