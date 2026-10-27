package backend.vehiculos.controller;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.*;
import backend.vehiculos.seguridad.IdentidadVehiculos;
import backend.vehiculos.seguridad.SesionVehiculosNoValidada;
import backend.vehiculos.service.ConsultaRegistroVehiculoService;
import backend.vehiculos.service.RegistroVehiculoService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas MVC del contrato; la identidad es un doble solo de tests, no autenticación real. */
class VehiculosControllerTest {
    private static final UUID OP = UUID.fromString("1c6d2c42-2ddf-4f44-b540-3b83a8519571");
    private MockMvc mvc;
    private ObjectMapper json;
    private ObjectProvider<IdentidadVehiculos> proveedores;
    private IdentidadVehiculos identidad;
    private RegistroVehiculoService registro;
    private ConsultaRegistroVehiculoService consultas;
    private Authentication autenticacion;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        json = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        proveedores = mock(ObjectProvider.class);
        identidad = mock(IdentidadVehiculos.class);
        registro = mock(RegistroVehiculoService.class);
        consultas = mock(ConsultaRegistroVehiculoService.class);
        autenticacion = UsernamePasswordAuthenticationToken.authenticated("fixture-solo-test", null, List.of());
        when(proveedores.getIfAvailable()).thenReturn(identidad);
        when(identidad.exigirUsuarioAutenticado(any())).thenReturn(17L);
        mvc = MockMvcBuilders.standaloneSetup(new VehiculosController(proveedores, registro, consultas))
                .setControllerAdvice(new ErroresVehiculosController())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Test void registroUsaIdentidadDelServidorYDevuelve201() throws Exception {
        when(registro.registrar(17L, datos())).thenReturn(resultado());
        mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos())))
                .andExpect(status().isCreated()).andExpect(jsonPath("idVehiculo").value(81))
                .andExpect(jsonPath("idOperacion").value(OP.toString()))
                .andExpect(jsonPath("kilometrajeActual").value(50000));
        verify(registro).registrar(17L, datos());
    }

    @Test void idUsuarioEnJsonQueryOCabeceraNoCambiaElPropietario() throws Exception {
        ObjectNode cuerpo = json.valueToTree(datos());
        cuerpo.put("idUsuario", 999);
        when(registro.registrar(17L, datos())).thenReturn(resultado());
        mvc.perform(post("/api/v1/vehiculos?idUsuario=999").principal(autenticacion)
                        .header("X-Usuario-Id", "999").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated());
        verify(registro).registrar(17L, datos());
        verify(registro, never()).registrar(eq(999L), any());
    }

    @Test void sinAutenticacionNoEjecutaElRegistro() throws Exception {
        mvc.perform(post("/api/v1/vehiculos").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(datos())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("codigo").value("SESION_NO_VALIDA"));
        verifyNoInteractions(registro, consultas, identidad);
    }

    @Test void autenticacionAnonimaNoAccedeALosCatalogos() throws Exception {
        var anonima = new AnonymousAuthenticationToken("fixture", "anonimo",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        mvc.perform(get("/api/v1/vehiculos/catalogos").principal(anonima))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registro, consultas, identidad);
    }

    @Test void credencialNoAutenticadaSeRechaza() throws Exception {
        var pendiente = UsernamePasswordAuthenticationToken.unauthenticated("fixture", null);
        mvc.perform(get("/api/v1/vehiculos/cupo-registro").principal(pendiente))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registro, consultas, identidad);
    }

    @Test void sinAdaptadorDeElsaSeRechazaAunqueSpringTengaUsuario() throws Exception {
        when(proveedores.getIfAvailable()).thenReturn(null);
        mvc.perform(get("/api/v1/vehiculos/catalogos").principal(autenticacion))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registro, consultas);
    }

    @Test void identidadResueltaInvalidaSeRechaza() throws Exception {
        when(identidad.exigirUsuarioAutenticado(any())).thenReturn(0L);
        mvc.perform(get("/api/v1/vehiculos/cupo-registro").principal(autenticacion))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registro, consultas);
    }

    @Test void sesionRechazadaPorElAdaptadorDevuelve401() throws Exception {
        when(identidad.exigirUsuarioAutenticado(any())).thenThrow(new SesionVehiculosNoValidada());
        mvc.perform(get("/api/v1/vehiculos/catalogos").principal(autenticacion))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registro, consultas);
    }

    @Test void catalogosProvienenDelServicioParaElUsuarioReal() throws Exception {
        when(consultas.consultarCatalogos(17L)).thenReturn(new DatosCatalogosVehiculo(
                List.of(new DatosCatalogosVehiculo.UsoVehiculo("PARTICULAR", "Particular")),
                List.of(new DatosCatalogosVehiculo.TipoMotor(1L, "ELECTRICO", "Eléctrico", true)),
                List.of(), List.of()));
        mvc.perform(get("/api/v1/vehiculos/catalogos").principal(autenticacion))
                .andExpect(status().isOk()).andExpect(jsonPath("usos[0].codigo").value("PARTICULAR"))
                .andExpect(jsonPath("tiposMotor[0].esElectrico").value(true));
        verify(consultas).consultarCatalogos(17L);
    }

    @Test void cupoAgotadoSeDevuelveComoEstadoRealSinEscrituras() throws Exception {
        when(consultas.consultarCupo(17L)).thenReturn(new DatosCupoRegistro("FREE", 1, 1, false));
        mvc.perform(get("/api/v1/vehiculos/cupo-registro").principal(autenticacion))
                .andExpect(status().isOk()).andExpect(jsonPath("puedeRegistrar").value(false))
                .andExpect(jsonPath("registrados").value(1));
        verifyNoInteractions(registro);
    }

    @Test void consultaOperacionConfirmadaConSuResultado() throws Exception {
        when(consultas.consultarOperacion(17L, OP)).thenReturn(new DatosEstadoOperacionVehiculo(OP, "CONFIRMADA", resultado()));
        mvc.perform(get("/api/v1/vehiculos/operaciones/" + OP).principal(autenticacion))
                .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CONFIRMADA"))
                .andExpect(jsonPath("resultado.idVehiculo").value(81));
        verify(consultas).consultarOperacion(17L, OP);
        verifyNoInteractions(registro);
    }

    @Test void operacionAusenteOAjenaDevuelve404SinDatosAjenos() throws Exception {
        when(consultas.consultarOperacion(17L, OP)).thenThrow(new ErrorRegistroVehiculo("OPERACION_NO_ENCONTRADA"));
        mvc.perform(get("/api/v1/vehiculos/operaciones/" + OP).principal(autenticacion))
                .andExpect(status().isNotFound()).andExpect(jsonPath("codigo").value("OPERACION_NO_ENCONTRADA"))
                .andExpect(jsonPath("resultado").doesNotExist());
    }

    @Test void uuidInvalidoSeRechazaCon400() throws Exception {
        mvc.perform(get("/api/v1/vehiculos/operaciones/no-es-uuid").principal(autenticacion))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("codigo").value("SOLICITUD_INVALIDA"));
        verifyNoInteractions(registro, consultas);
    }

    @Test void jsonMalFormadoSeRechazaCon400() throws Exception {
        mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registro, consultas);
    }

    @Test void validacionDevuelveLosNombresDeCampos() throws Exception {
        when(registro.registrar(17L, datos())).thenThrow(new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("numeroVin")));
        mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("campos", hasItem("numeroVin")));
    }

    @ParameterizedTest
    @CsvSource({"CUENTA_NO_AUTORIZADA,403", "PLAN_FREE_NO_DISPONIBLE,409", "LIMITE_FREE,409",
            "PLACA_DUPLICADA,409", "VIN_DUPLICADO,409", "CONFLICTO_REGISTRO,409",
            "CONFLICTO_IDEMPOTENCIA,409", "OPERACION_EN_PROCESO,409"})
    void erroresFuncionalesTienenEstadoHttp(String codigo, int estado) throws Exception {
        when(registro.registrar(17L, datos())).thenThrow(new ErrorRegistroVehiculo(codigo));
        mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos())))
                .andExpect(status().is(estado)).andExpect(jsonPath("codigo").value(codigo));
    }

    @Test void errorDeBaseNoSePresentaComoCupoVacioNiExponeSql() throws Exception {
        when(consultas.consultarCupo(17L)).thenThrow(new DataAccessResourceFailureException("secreto-SQL"));
        mvc.perform(get("/api/v1/vehiculos/cupo-registro").principal(autenticacion))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("codigo").value("SERVICIO_NO_DISPONIBLE"))
                .andExpect(jsonPath("puedeRegistrar").doesNotExist())
                .andExpect(content().string(not(containsString("secreto-SQL"))));
    }

    @Test void falloInesperadoNoSePresentaComoExitoNiExponeDetalles() throws Exception {
        when(registro.registrar(17L, datos())).thenThrow(new IllegalStateException("secreto-interno"));
        mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("codigo").value("ERROR_INTERNO"))
                .andExpect(content().string(not(containsString("secreto-interno"))));
    }

    @Test void mismaRespuestaDeRegistroNoSeReemplazaPorOtraEnElControlador() throws Exception {
        when(registro.registrar(17L, datos())).thenReturn(resultado());
        String uno = mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String dos = mvc.perform(post("/api/v1/vehiculos").principal(autenticacion)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(uno), json.readTree(dos));
    }

    private DatosRegistroVehiculo datos() {
        return new DatosRegistroVehiculo(OP, "Toyota", "Corolla", "PARTICULAR", "A1B234", 2020,
                "Azul", 1L, 1L, 50000, null, null, null, null, null, null, null);
    }

    private ResultadoRegistroVehiculo resultado() {
        return new ResultadoRegistroVehiculo(OP, 81, "A1B234", true, 50000,
                300, Instant.parse("2026-10-27T12:00:00Z"), 0);
    }
}
