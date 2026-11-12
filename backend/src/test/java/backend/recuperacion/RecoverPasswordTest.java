package backend.recuperacion;

import backend.registro.RegistrationConfiguration;
import backend.registro.verificacion.DevMailGateway;
import backend.registro.verificacion.MailGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecoverPasswordTest {
    private final RecoveryStore store = mock(RecoveryStore.class);
    private final PasswordEncoder encoder = new RegistrationConfiguration().registrationPasswordEncoder();
    private final DevMailGateway devMail = new DevMailGateway();
    private RecoverPassword service;
    private final AtomicReference<String> lastHash = new AtomicReference<>();
    private final AtomicReference<String> lastCode = new AtomicReference<>();
    private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");

    @BeforeEach
    void setUp() {
        ObjectProvider<DevMailGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(devMail);
        MailGateway mail = message -> {
            lastCode.set(message.code());
            return new MailGateway.Accepted("test");
        };
        service = new RecoverPassword(store, encoder, mail, provider, true);
        when(store.now()).thenReturn(NOW);
        when(store.findLatest(anyLong())).thenReturn(Optional.empty());
    }

    @Test
    void emitCreatesCodeForActiveAccount() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.insertCode(eq(7L), anyString(), any(), any())).thenAnswer(inv -> {
            lastHash.set(inv.getArgument(1));
            return 1L;
        });

        var result = service.emit("Ana@Example.com");
        assertEquals("CODIGO_ENVIADO_SI_APLICA", result.resultado());
        assertNotNull(result.codigoDesarrollo());
        assertEquals(6, result.codigoDesarrollo().length());
        verify(store).invalidateActive(7L);
        verify(store).insertCode(eq(7L), anyString(), any(), any());
    }

    @Test
    void emitRejectsUnknownEmail() {
        when(store.findByEmail("no@existe.com")).thenReturn(Optional.empty());
        RecoveryException ex = assertThrows(RecoveryException.class, () -> service.emit("no@existe.com"));
        assertEquals(RecoveryException.Reason.VALIDATION, ex.reason());
        assertTrue(ex.fieldErrors().get("correo").contains("registrada"));
        verify(store, never()).insertCode(anyLong(), anyString(), any(), any());
    }

    @Test
    void emitRejectsInactiveAccount() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "SUSPENDIDA",
                        encoder.encode("ViejaClave1!"))));
        RecoveryException ex = assertThrows(RecoveryException.class, () -> service.emit("ana@example.com"));
        assertEquals(RecoveryException.Reason.VALIDATION, ex.reason());
        assertTrue(ex.fieldErrors().get("correo").contains("activa"));
        verify(store, never()).insertCode(anyLong(), anyString(), any(), any());
    }

    @Test
    void emitCooldownBlocksResendWithin60Seconds() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.findLatest(7L)).thenReturn(Optional.of(new RecoveryStore.CodeRow(
                1L, 7L, "h", "ACTIVO", NOW.plusSeconds(900), NOW.minusSeconds(10), 0)));

        RecoveryException ex = assertThrows(RecoveryException.class, () -> service.emit("ana@example.com"));
        assertEquals(RecoveryException.Reason.VALIDATION, ex.reason());
        assertTrue(ex.fieldErrors().get("correo").contains("minuto"));
        verify(store, never()).insertCode(anyLong(), anyString(), any(), any());
        verify(store, never()).invalidateActive(anyLong());
    }

    @Test
    void emitAllowsResendAfterCooldown() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.findLatest(7L)).thenReturn(Optional.of(new RecoveryStore.CodeRow(
                1L, 7L, "h", "ACTIVO", NOW.plusSeconds(900), NOW.minusSeconds(61), 0)));
        when(store.insertCode(eq(7L), anyString(), any(), any())).thenReturn(2L);

        var result = service.emit("ana@example.com");
        assertEquals("CODIGO_ENVIADO_SI_APLICA", result.resultado());
        verify(store).insertCode(eq(7L), anyString(), any(), any());
    }

    @Test
    void emitFailsWhenMailRejectedWithoutExposingCode() {
        ObjectProvider<DevMailGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        MailGateway failing = message -> new MailGateway.Rejected(true);
        service = new RecoverPassword(store, encoder, failing, provider, false);
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.insertCode(eq(7L), anyString(), any(), any())).thenReturn(42L);

        RecoveryException ex = assertThrows(RecoveryException.class, () -> service.emit("ana@example.com"));
        assertEquals(RecoveryException.Reason.UNAVAILABLE, ex.reason());
        verify(store).markInvalidated(42L);
    }

    @Test
    void resetConsumesCodeAndUpdatesHash() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        String code = "123456";
        String hash = sha(code);
        when(store.lockLatestActive(7L)).thenReturn(Optional.of(
                new RecoveryStore.CodeRow(9L, 7L, hash, "ACTIVO", Instant.parse("2026-11-05T16:00:00Z"),
                        NOW.minusSeconds(120), 0)));
        when(store.lockPasswordHash(7L)).thenReturn(Optional.of(encoder.encode("ViejaClave1!")));
        when(store.updatePassword(eq(7L), anyString())).thenReturn(1);

        service.resetPassword("ana@example.com", code, "NuevaClave1!", "NuevaClave1!");

        verify(store).lockPasswordHash(7L);
        verify(store).updatePassword(eq(7L), argThat(h -> encoder.matches("NuevaClave1!", h)));
        verify(store).markConsumed(eq(9L), any());
        verify(store).invalidateActive(7L);
    }

    @Test
    void resetRejectsSameAsCurrentPassword() {
        String current = "MismaClave1!";
        String storedHash = encoder.encode(current);
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA", storedHash)));
        when(store.lockPasswordHash(7L)).thenReturn(Optional.of(storedHash));
        String code = "123456";
        when(store.lockLatestActive(7L)).thenReturn(Optional.of(
                new RecoveryStore.CodeRow(9L, 7L, sha(code), "ACTIVO", NOW.plusSeconds(900),
                        NOW.minusSeconds(60), 0)));

        RecoveryException ex = assertThrows(RecoveryException.class,
                () -> service.resetPassword("ana@example.com", code, current, current));
        assertEquals(RecoveryException.Reason.VALIDATION, ex.reason());
        assertTrue(ex.fieldErrors().get("nuevaContrasena").contains("distinta"));
        verify(store).lockPasswordHash(7L);
        verify(store, never()).updatePassword(anyLong(), anyString());
        verify(store, never()).markConsumed(anyLong(), any());
    }

    /** RF03 EA1: código incorrecto no modifica la contraseña. */
    @Test
    void ea1WrongCodeDoesNotUpdatePassword() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.lockLatestActive(7L)).thenReturn(Optional.of(
                new RecoveryStore.CodeRow(9L, 7L, sha("123456"), "ACTIVO", NOW.plusSeconds(900),
                        NOW.minusSeconds(60), 0)));

        assertThrows(RecoveryException.class,
                () -> service.resetPassword("ana@example.com", "000000", "NuevaClave1!", "NuevaClave1!"));
        verify(store, never()).updatePassword(anyLong(), anyString());
        verify(store).incrementFailures(9L);
    }

    /** RF03 EA1: código expirado no modifica la contraseña. */
    @Test
    void ea1ExpiredCodeDoesNotUpdatePassword() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.lockLatestActive(7L)).thenReturn(Optional.of(
                new RecoveryStore.CodeRow(9L, 7L, sha("123456"), "ACTIVO", NOW.minusSeconds(1),
                        NOW.minusSeconds(900), 0)));

        RecoveryException ex = assertThrows(RecoveryException.class,
                () -> service.resetPassword("ana@example.com", "123456", "NuevaClave1!", "NuevaClave1!"));
        assertEquals(RecoveryException.Reason.EXPIRED, ex.reason());
        verify(store, never()).updatePassword(anyLong(), anyString());
        verify(store).markInvalidated(9L);
    }

    /** RF03 EA1: código ya consumido no permite otro cambio. */
    @Test
    void ea1ConsumedCodeDoesNotUpdatePassword() {
        when(store.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(new RecoveryStore.Account(7L, "ana@example.com", "ACTIVA",
                        encoder.encode("ViejaClave1!"))));
        when(store.lockLatestActive(7L)).thenReturn(Optional.empty());

        assertThrows(RecoveryException.class,
                () -> service.resetPassword("ana@example.com", "123456", "NuevaClave1!", "NuevaClave1!"));
        verify(store, never()).updatePassword(anyLong(), anyString());
    }

    private static String sha(String code) {
        try {
            var d = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(d.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
