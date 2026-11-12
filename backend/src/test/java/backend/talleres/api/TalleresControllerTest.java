package backend.talleres.api;

import backend.autenticacion.SessionAuthentication;
import backend.talleres.model.DatosTaller.*;
import backend.talleres.model.TallerNombreDuplicado;
import backend.talleres.model.TallerNoDisponible;
import backend.talleres.model.TallerVersionConflicto;
import backend.talleres.service.TallerService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TalleresControllerTest {
    private TallerService servicio;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken sesion =
            UsernamePasswordAuthenticationToken.authenticated(
                    new SessionAuthentication.Principal(7, 1, UUID.randomUUID(), Instant.now().plusSeconds(3600)),
                    "", List.of());

    @BeforeEach void preparar() {
        servicio = mock(TallerService.class);
        mvc = standaloneSetup(new TalleresController(servicio))
                .setControllerAdvice(new ErroresTalleresController()).build();
    }

    private Taller ejemplo() {
        return new Taller(11, "Taller Centro", null, null, null, null, "ACTIVO", false, 0, Instant.parse("2026-11-06T12:00:00Z"));
    }

    @Test void sinSesion401() throws Exception {
        mvc.perform(get("/api/v1/rf34/talleres")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test void listarUsaIdentidadSesion() throws Exception {
        when(servicio.listar(7)).thenReturn(new ListaTalleres(List.of(ejemplo())));
        mvc.perform(get("/api/v1/rf34/talleres").principal(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.talleres[0].idTaller").value(11))
                .andExpect(jsonPath("$.talleres[0].nombre").value("Taller Centro"));
        verify(servicio).listar(7);
    }

    @Test void registrar200() throws Exception {
        when(servicio.registrar(eq(7L), any())).thenReturn(ejemplo());
        mvc.perform(post("/api/v1/rf34/talleres").principal(sesion)
                        .contentType("application/json")
                        .content("{\"nombre\":\"Taller Centro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTaller").value(11));
    }

    @Test void actualizarVersionConflicto409() throws Exception {
        when(servicio.actualizar(eq(7L), eq(11L), any())).thenThrow(new TallerVersionConflicto());
        mvc.perform(put("/api/v1/rf34/talleres/11").principal(sesion)
                        .contentType("application/json")
                        .content("{\"nombre\":\"X\",\"versionFila\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TALLER_VERSION_CONFLICTO"));
    }

    @Test void retirarAjeno404() throws Exception {
        when(servicio.retirar(eq(7L), eq(99L), any())).thenThrow(new TallerNoDisponible());
        mvc.perform(post("/api/v1/rf34/talleres/99/retirar").principal(sesion)
                        .contentType("application/json")
                        .content("{\"versionFila\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TALLER_NO_DISPONIBLE"));
    }

    @Test void favoritoOk() throws Exception {
        when(servicio.marcarFavorito(7, 11)).thenReturn(
                new Taller(11, "Taller Centro", null, null, null, null, "ACTIVO", true, 0, Instant.now()));
        mvc.perform(post("/api/v1/rf34/talleres/11/favorito").principal(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorito").value(true));
    }

    @Test void datosInvalidos400() throws Exception {
        when(servicio.registrar(eq(7L), any())).thenThrow(new IllegalArgumentException("nombre: es obligatorio"));
        mvc.perform(post("/api/v1/rf34/talleres").principal(sesion)
                        .contentType("application/json")
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test void telefonoInvalido400() throws Exception {
        when(servicio.registrar(eq(7L), any()))
                .thenThrow(new IllegalArgumentException("El teléfono ingresado no es válido"));
        mvc.perform(post("/api/v1/rf34/talleres").principal(sesion)
                        .contentType("application/json")
                        .content("{\"nombre\":\"CENTRO\",\"telefono\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El teléfono ingresado no es válido"));
    }

    @Test void nombreDuplicado409() throws Exception {
        when(servicio.registrar(eq(7L), any())).thenThrow(new TallerNombreDuplicado());
        mvc.perform(post("/api/v1/rf34/talleres").principal(sesion)
                        .contentType("application/json")
                        .content("{\"nombre\":\"CENTRO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value("Ya tienes un taller registrado con este nombre"));
    }

    @Test void apiAusentePorDefectoYActivable() {
        var runner = new ApplicationContextRunner().withUserConfiguration(TalleresController.class)
                .withBean(TallerService.class, () -> servicio);
        runner.run(c -> assertTrue(c.getBeansOfType(TalleresController.class).isEmpty()));
        runner.withPropertyValues("mycar.rf34.api-habilitada=true")
                .run(c -> assertEquals(1, c.getBeansOfType(TalleresController.class).size()));
    }
}
