package backend.vehiculos.seguridad;

import backend.autenticacion.SessionAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Solo acepta el principal tipado creado después de validar JWT y sesión persistida. */
@Component
public final class IdentidadSesionVehiculos implements IdentidadVehiculos {
    @Override public long exigirUsuarioAutenticado(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || !(autenticacion.getPrincipal() instanceof SessionAuthentication.Principal principal)
                || principal.userId() <= 0 || principal.sessionId() <= 0) {
            throw new SesionVehiculosNoValidada();
        }
        return principal.userId();
    }
}
