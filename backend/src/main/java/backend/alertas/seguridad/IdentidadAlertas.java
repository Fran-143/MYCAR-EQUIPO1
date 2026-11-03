package backend.alertas.seguridad;

import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface IdentidadAlertas {
    long exigirUsuario(Authentication autenticacion);
}
