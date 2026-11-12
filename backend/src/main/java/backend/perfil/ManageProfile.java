package backend.perfil;

import backend.registro.RegistrationException;
import backend.registro.RegistrationValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ManageProfile {
    private final ProfileStore store;
    private final RegistrationValidator validator;
    private final PasswordEncoder passwords;

    public ManageProfile(ProfileStore store, RegistrationValidator validator,
            @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords) {
        this.store = store;
        this.validator = validator;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public ProfileData consult(long userId) {
        return store.findActiveProfile(userId).orElseThrow(ProfileException::notFound);
    }

    @Transactional
    public ProfileData updatePersonalData(long userId, String nombre, String apellido, LocalDate fechaNacimiento) {
        RegistrationValidator.PersonalData personal;
        try {
            personal = validator.validatePersonalData(nombre, apellido, fechaNacimiento);
        } catch (RegistrationException ex) {
            throw ProfileException.validation(ex.fieldErrors());
        }
        if (store.updatePersonalData(userId, personal.nombre(), personal.apellido(), personal.fechaNacimiento()) != 1) {
            throw ProfileException.notFound();
        }
        return store.findActiveProfile(userId).orElseThrow(ProfileException::notFound);
    }

    /** RF05 paso 1: confirma que el usuario conoce la contraseña actual antes de liberar el cambio. */
    @Transactional
    public void verifyCurrentPassword(long userId, String currentPassword) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw ProfileException.validation(Map.of("contrasenaActual", "Ingresa tu contraseña actual."));
        }
        // Requiere transacción escribible: lockCredential usa FOR NO KEY UPDATE.
        var credential = store.lockCredential(userId).orElseThrow(ProfileException::notFound);
        if (!"ACTIVA".equals(credential.estadoCuenta())) throw ProfileException.notFound();
        if (!passwords.matches(currentPassword, credential.passwordHash())) {
            throw ProfileException.validation(Map.of("contrasenaActual", "La contraseña actual no es correcta."));
        }
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword, String confirmation) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (currentPassword == null || currentPassword.isEmpty()) {
            errors.put("contrasenaActual", "Ingresa tu contraseña actual.");
        }
        if (!RegistrationValidator.isValidPassword(newPassword)) {
            errors.put("nuevaContrasena",
                    "Usa 8–64 caracteres con mayúscula, minúscula, dígito y especial, sin espacios.");
        }
        if (newPassword == null || confirmation == null || !newPassword.equals(confirmation)) {
            errors.put("confirmacionContrasena", "Las contraseñas deben coincidir exactamente.");
        }
        if (currentPassword != null && newPassword != null && currentPassword.equals(newPassword)) {
            errors.put("nuevaContrasena", "La nueva contraseña debe ser distinta de la actual.");
        }
        if (!errors.isEmpty()) throw ProfileException.validation(errors);

        var credential = store.lockCredential(userId).orElseThrow(ProfileException::notFound);
        if (!"ACTIVA".equals(credential.estadoCuenta())) throw ProfileException.notFound();
        if (!passwords.matches(currentPassword, credential.passwordHash())) {
            throw ProfileException.validation(Map.of("contrasenaActual", "La contraseña actual no es correcta."));
        }
        // Defensa extra: no permitir reutilizar el hash vigente aunque el cliente omita la comparación en claro.
        if (passwords.matches(newPassword, credential.passwordHash())) {
            throw ProfileException.validation(Map.of("nuevaContrasena",
                    "La nueva contraseña debe ser distinta de la actual."));
        }
        if (store.updatePasswordHash(userId, passwords.encode(newPassword)) != 1) {
            throw ProfileException.notFound();
        }
    }
}
