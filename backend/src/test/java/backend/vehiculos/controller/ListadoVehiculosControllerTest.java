package backend.vehiculos.controller;

import backend.vehiculos.dto.DatosDetalleVehiculo;
import backend.vehiculos.dto.DatosListadoVehiculos;
import java.time.Instant;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ListadoVehiculosControllerTest {
    MockMvc mvc;
    ConsultaRegistroVehiculoService consultas;
    Authentication auth;
    @BeforeEach @SuppressWarnings("unchecked") void preparar() {
        var proveedor = mock(ObjectProvider.class);
        IdentidadVehiculos identidad = mock(IdentidadVehiculos.class);
        when(proveedor.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuarioAutenticado(any())).thenReturn(17L);
        consultas = mock(ConsultaRegistroVehiculoService.class);
        auth = UsernamePasswordAuthenticationToken.authenticated("fixture", null, List.of());
        mvc = MockMvcBuilders.standaloneSetup(new VehiculosController(proveedor,
                mock(RegistroVehiculoService.class), consultas))
                .setControllerAdvice(new ErroresVehiculosController()).build();
    }
    @Test void listadoUsaPropietarioDeSesionIgnorandoIdentificadoresLibres() throws Exception {
        when(consultas.listarVehiculos(17, "ACTIVO", "Toyota")).thenReturn(
                new DatosListadoVehiculos(List.of(), new DatosListadoVehiculos.ResumenEstados(1, 0, 1), "ACTIVO", "Toyota"));
        mvc.perform(get("/api/v1/vehiculos").principal(auth).param("filtro", "ACTIVO")
                .param("busqueda", "Toyota").param("idUsuario", "999").header("X-Usuario-Id", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("resumen.todos").value(1))
                .andExpect(jsonPath("vehiculos").isEmpty());
        verify(consultas).listarVehiculos(17, "ACTIVO", "Toyota");
    }
    @Test void anonimoNoEjecutaConsulta() throws Exception {
        mvc.perform(get("/api/v1/vehiculos")).andExpect(status().isUnauthorized());
        verifyNoInteractions(consultas);
    }
    @Test void valoresAusentesSeDeleganParaAplicarTodosEnServicio() throws Exception {
        when(consultas.listarVehiculos(17, null, null)).thenReturn(
                new DatosListadoVehiculos(List.of(), new DatosListadoVehiculos.ResumenEstados(0, 0, 0), "TODOS", ""));
        mvc.perform(get("/api/v1/vehiculos").principal(auth)).andExpect(status().isOk())
                .andExpect(jsonPath("filtro").value("TODOS"));
    }
    @Test void filtroInvalidoEs400() throws Exception {
        when(consultas.listarVehiculos(17, "OTRO", null)).thenThrow(new ErrorRegistroVehiculo("FILTRO_INVALIDO", Set.of("filtro")));
        mvc.perform(get("/api/v1/vehiculos").principal(auth).param("filtro", "OTRO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("FILTRO_INVALIDO"));
    }
    @Test void detalleUsaPropietarioDeSesion() throws Exception {
        var detalle = new DatosDetalleVehiculo(5, "A1B234", "Toyota", "Corolla", 2020, "Particular", "Gris",
                "Combustión", "Gasolina", "ACTIVO", true, 50000, Instant.parse("2020-01-01T00:00:00Z"),
                null, null, null, null, false);
        when(consultas.consultarDetalle(17, 5)).thenReturn(detalle);
        mvc.perform(get("/api/v1/vehiculos/5").principal(auth).header("X-Usuario-Id", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.placa").value("A1B234"));
        verify(consultas).consultarDetalle(17, 5);
    }
    @Test void detalleInexistenteEs404() throws Exception {
        when(consultas.consultarDetalle(17, 99)).thenThrow(new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/vehiculos/99").principal(auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }
    @Test void falloSqlEs503YNoListaVaciaNiDetalleTecnico() throws Exception {
        when(consultas.listarVehiculos(17, null, null)).thenThrow(new DataAccessResourceFailureException("detalle privado SQL"));
        mvc.perform(get("/api/v1/vehiculos").principal(auth)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("codigo").value("SERVICIO_NO_DISPONIBLE"))
                .andExpect(jsonPath("vehiculos").doesNotExist());
    }
}
