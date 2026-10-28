package backend.vehiculos.seguridad;

import backend.autenticacion.SessionAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IdentidadSesionVehiculosTest {
    final IdentidadSesionVehiculos identidad = new IdentidadSesionVehiculos();
    @Test void aceptaSoloPrincipalTipadoAutenticado() {
        var principal = new SessionAuthentication.Principal(17, 4, UUID.randomUUID(), Instant.now().plusSeconds(60));
        assertEquals(17, identidad.exigirUsuarioAutenticado(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
        assertThrows(SesionVehiculosNoValidada.class, () -> identidad.exigirUsuarioAutenticado(null));
        assertThrows(SesionVehiculosNoValidada.class, () -> identidad.exigirUsuarioAutenticado(UsernamePasswordAuthenticationToken.unauthenticated(principal, null)));
        assertThrows(SesionVehiculosNoValidada.class, () -> identidad.exigirUsuarioAutenticado(UsernamePasswordAuthenticationToken.authenticated("17", null, List.of())));
    }
}
