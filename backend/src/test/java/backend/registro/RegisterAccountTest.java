package backend.registro;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterAccountTest {
    private final RegistrationValidator validator = mock(RegistrationValidator.class);
    private final RegistrationStore store = mock(RegistrationStore.class);
    private final PasswordEncoder encoder = new RegistrationConfiguration().registrationPasswordEncoder();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
    private final RegistrationCommand command = RegistrationValidatorTest.command("Ana", LocalDate.of(1995,1,1), "ClaveSegura1!");
    private final RegistrationData data = new RegistrationData("Ana","Pérez","ana@example.com",LocalDate.of(1995,1,1),"FIXTURE-NO-LEGAL");

    @Test void argon2idHasRandomSaltExplicitCostAndNoPasswordTruncation() {
        String password = "Áb1!"+"ñ".repeat(60);
        String a = encoder.encode(password), b = encoder.encode(password);
        assertTrue(a.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertNotEquals(a,b);
        assertTrue(a.length()<=255);
        assertTrue(encoder.matches(password,a));
        assertFalse(encoder.matches(password.substring(0,password.length()-1),a));
        assertFalse(encoder.matches(password.toLowerCase(),a));
    }

    @Test void hashesOnlyAfterValidationAndStoresServerTime() {
        when(validator.validate(command)).thenReturn(data);
        when(store.createAccount(eq(data),anyString(),eq(clock.instant()))).thenAnswer(call -> {
            assertTrue(encoder.matches(command.contrasena(),call.getArgument(1)));
            return 41L;
        });
        assertEquals(41L,new RegisterAccount(validator,new RegistrationPrivacyPolicy("FIXTURE-NO-LEGAL"),store,encoder,clock).register(command));
        verify(store).emailExists(data.correo());
        verify(store).createAccount(eq(data),anyString(),eq(clock.instant()));
    }

    @Test void missingPrivacyVersionPreventsAllPersistence() {
        when(validator.validate(command)).thenReturn(data);
        assertThrows(RegistrationException.class, () -> new RegisterAccount(validator,new RegistrationPrivacyPolicy(""),store,encoder,clock).register(command));
        verifyNoInteractions(store);
    }

    @Test void existingEmailDoesNotCreateAnythingAndUsesGenericMessage() {
        when(validator.validate(command)).thenReturn(data);
        when(store.emailExists(data.correo())).thenReturn(true);
        var ex=assertThrows(RegistrationException.class, () -> new RegisterAccount(validator,new RegistrationPrivacyPolicy("FIXTURE-NO-LEGAL"),store,encoder,clock).register(command));
        assertEquals("Correo no disponible.",ex.getMessage());
        verify(store,never()).createAccount(any(),anyString(),any());
    }
}
