package backend.perfil;

import backend.autenticacion.SessionAuthentication;
import backend.perfil.web.ProfileController;
import backend.perfil.web.ProfileErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pruebas del controlador sin MockMvc Security (evita dependencia spring-security-test). */
class ProfileWebTest {
    private final ManageProfile profiles = mock(ManageProfile.class);
    private final ProfileController controller = new ProfileController(profiles);
    private final ProfileErrorHandler errors = new ProfileErrorHandler();

    private static SessionAuthentication.Principal principal(long userId) {
        return new SessionAuthentication.Principal(userId, 99L, UUID.randomUUID(), Instant.now().plusSeconds(3600));
    }

    @Test
    void getReturnsOwnProfile() {
        when(profiles.consult(7L)).thenReturn(
                new ProfileData("Ana", "Pérez", "ana@example.com", LocalDate.of(1995, 1, 1)));

        ResponseEntity<ProfileController.ProfileResponse> response = controller.get(principal(7L));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Ana Pérez", response.getBody().nombreCompleto());
        assertEquals("ana@example.com", response.getBody().correo());
    }

    @Test
    void putUpdatesAndReturnsProfile() {
        when(profiles.updatePersonalData(eq(7L), eq("Marcelo"), eq("Lopez"), eq(LocalDate.of(1998, 8, 15))))
                .thenReturn(new ProfileData("Marcelo", "Lopez", "ana@example.com", LocalDate.of(1998, 8, 15)));

        ResponseEntity<ProfileController.ProfileResponse> response = controller.put(principal(7L),
                new ProfileController.UpdateProfileRequest("Marcelo", "Lopez", LocalDate.of(1998, 8, 15)));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Marcelo Lopez", response.getBody().nombreCompleto());
    }

    @Test
    void putValidationErrorsSurfaceAs400() {
        when(profiles.updatePersonalData(anyLong(), any(), any(), any()))
                .thenThrow(ProfileException.validation(java.util.Map.of("nombre", "Usa 2–100 caracteres.")));

        ProfileException thrown = assertThrows(ProfileException.class, () ->
                controller.put(principal(7L),
                        new ProfileController.UpdateProfileRequest("X", "Lopez", LocalDate.of(1998, 8, 15))));
        var response = errors.profile(thrown);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDACION", response.getBody().codigo());
        assertTrue(response.getBody().erroresPorCampo().containsKey("nombre"));
    }
}
