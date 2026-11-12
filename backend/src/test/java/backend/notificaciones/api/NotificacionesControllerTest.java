package backend.notificaciones.api;

import backend.alertas.seguridad.IdentidadAlertas;
import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.model.ErrorNotificaciones;
import backend.notificaciones.service.NotificacionInternaService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificacionesControllerTest {
    MockMvc mvc;
    NotificacionInternaService servicio;
    Authentication auth;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        var proveedor = mock(ObjectProvider.class);
        IdentidadAlertas identidad = mock(IdentidadAlertas.class);
        when(proveedor.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuario(any())).thenReturn(12L);
        servicio = mock(NotificacionInternaService.class);
        auth = UsernamePasswordAuthenticationToken.authenticated("u", null, List.of());
        mvc = MockMvcBuilders.standaloneSetup(new NotificacionesController(servicio, proveedor))
                .setControllerAdvice(new ErroresNotificacionesController())
                .build();
    }

    @Test
    void listarBandejaOk() throws Exception {
        when(servicio.consultarBandeja(12L, "PENDIENTES", "TODAS", "TODAS"))
                .thenReturn(new ContratoNotificaciones.BandejaResponse(
                        "PENDIENTES", "TODAS", "TODAS", 2, 1, List.of()));
        mvc.perform(get("/api/v1/notificaciones").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contadorPendientesGlobal").value(2))
                .andExpect(jsonPath("$.contadorNoLeidasGlobal").value(1))
                .andExpect(jsonPath("$.filtroLectura").value("TODAS"));
    }

    @Test
    void filtroLecturaNoLeidas() throws Exception {
        when(servicio.consultarBandeja(12L, "PENDIENTES", "MANTENIMIENTO", "NO_LEIDAS"))
                .thenReturn(new ContratoNotificaciones.BandejaResponse(
                        "PENDIENTES", "MANTENIMIENTO", "NO_LEIDAS", 2, 1, List.of()));
        mvc.perform(get("/api/v1/notificaciones")
                        .param("categoria", "MANTENIMIENTO")
                        .param("filtroLectura", "NO_LEIDAS")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtroLectura").value("NO_LEIDAS"));
    }

    @Test
    void categoriaInvalida400() throws Exception {
        when(servicio.consultarBandeja(eq(12L), anyString(), eq("XYZ"), anyString()))
                .thenThrow(new ErrorNotificaciones("DATOS_INVALIDOS", "Categoría no válida."));
        mvc.perform(get("/api/v1/notificaciones").param("categoria", "XYZ").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void leerOk() throws Exception {
        when(servicio.marcarLeida(12L, 5L)).thenReturn(new ContratoNotificaciones.LeerResponse(
                5L, true, "PENDIENTE", "DETALLE_ALERTA", 9L, 7L));
        mvc.perform(post("/api/v1/notificaciones/5/leer").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true))
                .andExpect(jsonPath("$.estadoGestion").value("PENDIENTE"));
    }

    @Test
    void leerAjena404() throws Exception {
        when(servicio.marcarLeida(12L, 8L))
                .thenThrow(new ErrorNotificaciones("NOTIFICACION_NO_ENCONTRADA", "No se pudo cargar esta notificación."));
        mvc.perform(post("/api/v1/notificaciones/8/leer").principal(auth))
                .andExpect(status().isNotFound());
    }
}
