package backend.perfil;

import backend.registro.RegistrationConfiguration;
import backend.registro.RegistrationValidator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManageProfileTest {
    private final ProfileStore store = mock(ProfileStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC);
    private final RegistrationValidator validator = new RegistrationValidator(clock,
            Validation.buildDefaultValidatorFactory().getValidator());
    private final PasswordEncoder encoder = new RegistrationConfiguration().registrationPasswordEncoder();
    private ManageProfile service;

    @BeforeEach
    void setUp() {
        service = new ManageProfile(store, validator, encoder);
    }

    @Test
    void consultReturnsActiveProfile() {
        var data = new ProfileData("Ana", "Pérez", "ana@example.com", LocalDate.of(1995, 1, 1));
        when(store.findActiveProfile(7L)).thenReturn(Optional.of(data));

        assertEquals(data, service.consult(7L));
        assertEquals("Ana Pérez", data.nombreCompleto());
    }

    @Test
    void updatePersistsValidatedNombreApellidoAndBirth() {
        when(store.updatePersonalData(eq(7L), eq("Marcelo"), eq("Lopez"), eq(LocalDate.of(1998, 8, 15))))
                .thenReturn(1);
        when(store.findActiveProfile(7L)).thenReturn(Optional.of(
                new ProfileData("Marcelo", "Lopez", "ana@example.com", LocalDate.of(1998, 8, 15))));

        ProfileData updated = service.updatePersonalData(7L, "  Marcelo  ", "Lopez", LocalDate.of(1998, 8, 15));

        assertEquals("Marcelo", updated.nombre());
        assertEquals("Lopez", updated.apellido());
        verify(store).updatePersonalData(7L, "Marcelo", "Lopez", LocalDate.of(1998, 8, 15));
    }

    @Test
    void updateRejectsInvalidAgeWithoutWriting() {
        ProfileException ex = assertThrows(ProfileException.class,
                () -> service.updatePersonalData(7L, "Ana", "Pérez", LocalDate.of(2015, 1, 1)));
        assertEquals(ProfileException.Reason.VALIDATION, ex.reason());
        assertTrue(ex.fieldErrors().containsKey("fechaNacimiento"));
        verify(store, never()).updatePersonalData(anyLong(), any(), any(), any());
    }

    /** RF04 EA1: nombre inválido no persiste. */
    @Test
    void ea1InvalidNombreDoesNotPersist() {
        assertThrows(ProfileException.class,
                () -> service.updatePersonalData(7L, "X", "Pérez", LocalDate.of(1995, 1, 1)));
        verify(store, never()).updatePersonalData(anyLong(), any(), any(), any());
    }

    /** RF04 EA1: cuenta inactiva/ajena (UPDATE 0) no confirma éxito. */
    @Test
    void ea1ForeignOrMissingAccountRejectsUpdate() {
        when(store.updatePersonalData(eq(99L), eq("Marcelo"), eq("Lopez"), eq(LocalDate.of(1998, 8, 15))))
                .thenReturn(0);
        ProfileException ex = assertThrows(ProfileException.class,
                () -> service.updatePersonalData(99L, "Marcelo", "Lopez", LocalDate.of(1998, 8, 15)));
        assertEquals(ProfileException.Reason.NOT_FOUND, ex.reason());
    }

    @Test
    void changePasswordRequiresCurrentMatchAndPolicy() {
        String hash = encoder.encode("ClaveVieja1!");
        when(store.lockCredential(7L)).thenReturn(Optional.of(
                new ProfileStore.Credential(7L, hash, "ACTIVA")));
        when(store.updatePasswordHash(eq(7L), anyString())).thenReturn(1);

        service.changePassword(7L, "ClaveVieja1!", "ClaveNueva2@", "ClaveNueva2@");

        verify(store).updatePasswordHash(eq(7L), argThat(h -> encoder.matches("ClaveNueva2@", h)));
    }

    @Test
    void changePasswordRejectsWrongCurrent() {
        when(store.lockCredential(7L)).thenReturn(Optional.of(
                new ProfileStore.Credential(7L, encoder.encode("ClaveVieja1!"), "ACTIVA")));

        ProfileException ex = assertThrows(ProfileException.class,
                () -> service.changePassword(7L, "Incorrecta1!", "ClaveNueva2@", "ClaveNueva2@"));
        assertEquals("La contraseña actual no es correcta.", ex.fieldErrors().get("contrasenaActual"));
        verify(store, never()).updatePasswordHash(anyLong(), anyString());
    }

    @Test
    void changePasswordRejectsSameAsCurrent() {
        ProfileException ex = assertThrows(ProfileException.class,
                () -> service.changePassword(7L, "ClaveIgual1!", "ClaveIgual1!", "ClaveIgual1!"));
        assertTrue(ex.fieldErrors().get("nuevaContrasena").contains("distinta"));
        verifyNoInteractions(store);
    }

    /** RF05 EA1: nueva contraseña débil no actualiza hash. */
    @Test
    void ea1WeakNewPasswordDoesNotUpdate() {
        ProfileException ex = assertThrows(ProfileException.class,
                () -> service.changePassword(7L, "ClaveVieja1!", "debil", "debil"));
        assertEquals(ProfileException.Reason.VALIDATION, ex.reason());
        assertTrue(ex.fieldErrors().containsKey("nuevaContrasena"));
        verify(store, never()).updatePasswordHash(anyLong(), anyString());
        verify(store, never()).lockCredential(anyLong());
    }

    @Test
    void verifyCurrentPasswordAcceptsCorrectSecret() {
        when(store.lockCredential(7L)).thenReturn(Optional.of(
                new ProfileStore.Credential(7L, encoder.encode("ClaveVieja1!"), "ACTIVA")));
        assertDoesNotThrow(() -> service.verifyCurrentPassword(7L, "ClaveVieja1!"));
        verify(store, never()).updatePasswordHash(anyLong(), anyString());
    }

    @Test
    void verifyCurrentPasswordRejectsWrongSecret() {
        when(store.lockCredential(7L)).thenReturn(Optional.of(
                new ProfileStore.Credential(7L, encoder.encode("ClaveVieja1!"), "ACTIVA")));
        ProfileException ex = assertThrows(ProfileException.class,
                () -> service.verifyCurrentPassword(7L, "Incorrecta1!"));
        assertEquals("La contraseña actual no es correcta.", ex.fieldErrors().get("contrasenaActual"));
    }
}
