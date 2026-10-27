package backend.registro;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationValidatorTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    // UTC is already 27 October, but it is still 26 October in Lima.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T02:00:00Z"), ZoneOffset.UTC);
    private final RegistrationValidator validator = new RegistrationValidator(CLOCK, FACTORY.getValidator());
    private static final String SECRET = "ClaveSegura1!";

    @AfterAll static void closeFactory() { FACTORY.close(); }

    static RegistrationCommand command(String name, LocalDate birth, String password) {
        return new RegistrationCommand(name, "D'Ávila", "  Elsa.Test+Curso@Example.COM  ", birth,
                password, password, true, "FIXTURE-NO-LEGAL");
    }

    @Test void normalizesNamesAndEmailWithoutProviderRulesOrSemanticCatalog() {
        var result = validator.validate(command("  Xyzzy\t  María  ", LocalDate.of(1995, 1, 1), SECRET));
        assertEquals("Xyzzy María", result.nombre());
        assertEquals("D'Ávila", result.apellido());
        assertEquals("elsa.test+curso@example.com", result.correo());
    }

    @ParameterizedTest @ValueSource(strings = {"A1", "-Ana", "Ana-", "Ana--María", "Ana_'", "A", " ", "Ana@María"})
    void rejectsInvalidNameFormat(String name) {
        var ex = assertThrows(RegistrationException.class, () -> validator.validate(command(name, LocalDate.of(1995,1,1),SECRET)));
        assertTrue(ex.fieldErrors().containsKey("nombre"));
    }

    @Test void nameLengthBoundariesAndUnicodeLetters() {
        validator.validate(command("Á".repeat(100), LocalDate.of(1995,1,1), SECRET));
        validator.validate(command("José-María", LocalDate.of(1995,1,1), SECRET));
        assertThrows(RegistrationException.class, () -> validator.validate(command("A".repeat(101),LocalDate.of(1995,1,1),SECRET)));
    }

    @Test void ageUsesCompletedYearsAndLimaDate() {
        validator.validate(command("Ana", LocalDate.of(2008,10,26), SECRET));
        validator.validate(command("Ana", LocalDate.of(1940,10,27), SECRET)); // 85, not 86 yet
        assertThrows(RegistrationException.class, () -> validator.validate(command("Ana",LocalDate.of(2008,10,27),SECRET)));
        assertThrows(RegistrationException.class, () -> validator.validate(command("Ana",LocalDate.of(1940,10,26),SECRET)));
        assertThrows(RegistrationException.class, () -> validator.validate(command("Ana",LocalDate.of(2026,10,27),SECRET)));
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"Abc1!", "abcdefgh1!", "ABCDEFGH1!", "Abcdefghi!", "Abcdefghi1", "Abcd 123!", "Abcd\t123!", "Abcd\u00a0123!"})
    void rejectsInvalidPasswordWithoutLeakingIt(String password) {
        var ex = assertThrows(RegistrationException.class, () -> validator.validate(command("Ana",LocalDate.of(1995,1,1),password)));
        assertTrue(ex.fieldErrors().containsKey("contrasena"));
        if (password != null && !password.isEmpty()) assertFalse(ex.toString().contains(password));
    }

    @Test void passwordBoundariesAndExactConfirmation() {
        validator.validate(command("Ana",LocalDate.of(1995,1,1),"Abcde1!x"));
        validator.validate(command("Ana",LocalDate.of(1995,1,1),"Áb1!"+"ñ".repeat(60)));
        assertThrows(RegistrationException.class, () -> validator.validate(command("Ana",LocalDate.of(1995,1,1),"Ab1!"+"x".repeat(61))));
        var valid = command("Ana",LocalDate.of(1995,1,1),SECRET);
        var mismatch = new RegistrationCommand(valid.nombre(),valid.apellido(),valid.correo(),valid.fechaNacimiento(),SECRET,SECRET.toLowerCase(),true,valid.versionPrivacidad());
        assertTrue(assertThrows(RegistrationException.class, () -> validator.validate(mismatch)).fieldErrors().containsKey("confirmacionContrasena"));
    }

    @Test void missingFieldsAndConsentAreRevalidatedByBackend() {
        var ex = assertThrows(RegistrationException.class, () -> validator.validate(new RegistrationCommand(null,null,null,null,null,null,false,null)));
        assertEquals(8, ex.fieldErrors().size());
        assertThrows(RegistrationException.class, () -> validator.validate(null));
    }

    @Test void privacyHasNoLegalDefaultAndRequiresExactApprovedVersion() {
        assertEquals(RegistrationException.Reason.REGISTRATION_UNAVAILABLE,
                assertThrows(RegistrationException.class, () -> new RegistrationPrivacyPolicy("").requireAcceptedVersion("FIXTURE-NO-LEGAL")).reason());
        assertThrows(RegistrationException.class, () -> new RegistrationPrivacyPolicy("FIXTURE-NO-LEGAL").requireAcceptedVersion("otra"));
        new RegistrationPrivacyPolicy("FIXTURE-NO-LEGAL").requireAcceptedVersion("FIXTURE-NO-LEGAL");
    }

    @Test void commandsDoNotRenderSecretsInToString() {
        assertFalse(command("Ana",LocalDate.of(1995,1,1),SECRET).toString().contains(SECRET));
    }
}
