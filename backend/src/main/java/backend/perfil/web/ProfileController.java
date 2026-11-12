package backend.perfil.web;

import backend.autenticacion.SessionAuthentication;
import backend.perfil.ManageProfile;
import backend.perfil.ProfileData;
import backend.perfil.ProfileException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/perfil")
public class ProfileController {
    private final ManageProfile profiles;

    public ProfileController(ManageProfile profiles) { this.profiles = profiles; }

    public record ProfileResponse(String nombre, String apellido, String correo, String fechaNacimiento,
            String nombreCompleto) {
        static ProfileResponse from(ProfileData data) {
            return new ProfileResponse(data.nombre(), data.apellido(), data.correo(),
                    data.fechaNacimiento() == null ? null : data.fechaNacimiento().toString(),
                    data.nombreCompleto());
        }
    }

    public record UpdateProfileRequest(String nombre, String apellido, LocalDate fechaNacimiento) { }

    public record ChangePasswordRequest(String contrasenaActual, String nuevaContrasena,
            String confirmacionContrasena) { }

    public record VerifyPasswordRequest(String contrasenaActual) { }

    public record PasswordChangedResponse(String resultado) { }

    public record PasswordVerifiedResponse(String resultado) { }

    @GetMapping(produces = "application/json")
    public ResponseEntity<ProfileResponse> get(@AuthenticationPrincipal SessionAuthentication.Principal principal) {
        long userId = requireUser(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ProfileResponse.from(profiles.consult(userId)));
    }

    @PutMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<ProfileResponse> put(@AuthenticationPrincipal SessionAuthentication.Principal principal,
            @RequestBody UpdateProfileRequest request) {
        long userId = requireUser(principal);
        if (request == null) throw ProfileException.validation(java.util.Map.of("perfil", "Datos obligatorios."));
        ProfileData updated = profiles.updatePersonalData(userId, request.nombre(), request.apellido(),
                request.fechaNacimiento());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ProfileResponse.from(updated));
    }

    @PostMapping(value = "/contrasena/verificar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<PasswordVerifiedResponse> verifyPassword(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @RequestBody VerifyPasswordRequest request) {
        long userId = requireUser(principal);
        if (request == null) throw ProfileException.validation(java.util.Map.of("contrasena", "Datos obligatorios."));
        profiles.verifyCurrentPassword(userId, request.contrasenaActual());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PasswordVerifiedResponse("CONTRASENA_ACTUAL_VALIDA"));
    }

    @PostMapping(value = "/contrasena", consumes = "application/json", produces = "application/json")
    public ResponseEntity<PasswordChangedResponse> changePassword(
            @AuthenticationPrincipal SessionAuthentication.Principal principal,
            @RequestBody ChangePasswordRequest request) {
        long userId = requireUser(principal);
        if (request == null) throw ProfileException.validation(java.util.Map.of("contrasena", "Datos obligatorios."));
        profiles.changePassword(userId, request.contrasenaActual(), request.nuevaContrasena(),
                request.confirmacionContrasena());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PasswordChangedResponse("CONTRASENA_ACTUALIZADA"));
    }

    private static long requireUser(SessionAuthentication.Principal principal) {
        if (principal == null || principal.userId() <= 0) throw ProfileException.unauthorized();
        return principal.userId();
    }
}
