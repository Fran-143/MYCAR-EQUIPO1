package backend.mantenimiento.seguridad;

import org.springframework.security.core.Authentication;

/**
 * Adaptador pendiente de Elsa. Resolver usuario desde sesión MyCar vigente y cuenta
 * ACTIVA; validar revocación/expiración. Nunca analizar Authentication.getName()
 * como idUsuario ni aceptar un idUsuario desde JSON, query o cabecera libre.
 * Reutilizar su sesión real; esta interfaz no crea otro sistema de login.
 */
public interface IdentidadMantenimiento {
    long exigirUsuarioAutenticado(Authentication autenticacion);
}
