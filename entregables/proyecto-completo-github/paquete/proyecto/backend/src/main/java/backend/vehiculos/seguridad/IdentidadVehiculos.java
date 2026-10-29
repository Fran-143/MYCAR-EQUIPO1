package backend.vehiculos.seguridad;

import org.springframework.security.core.Authentication;

/**
 * Puerto de identidad integrado mediante IdentidadSesionVehiculos y SessionBearerFilter.
 * Debe validar sesión vigente/no revocada/no expirada y resolver el usuario real.
 * No aceptar idUsuario desde JSON, query, cabeceras libres o el nombre textual
 * del usuario de desarrollo. Si no hay una sesión válida, lanzar
 * SesionVehiculosNoValidada. El controlador verifica además autenticación no anónima.
 */
public interface IdentidadVehiculos {
    long exigirUsuarioAutenticado(Authentication autenticacion);
}
