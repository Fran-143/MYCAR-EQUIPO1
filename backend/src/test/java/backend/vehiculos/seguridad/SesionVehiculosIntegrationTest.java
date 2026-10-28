package backend.vehiculos.seguridad;

import backend.registro.RegisterAccount;
import backend.registro.RegistrationCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="mycar.sesion.vehiculos.tests", matches="true")
@SpringBootTest(properties={"mycar.registro.privacidad.version-vigente=FIXTURE-INTEGRACION-NO-LEGAL",
        "mycar.auth.access-ttl=PT5M", "mycar.auth.session-ttl=P1D",
        "mycar.rf17.api-habilitada=true", "mycar.rf18.api-habilitada=true"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class SesionVehiculosIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        String url=System.getenv("MYCAR_TEST_JDBC_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_mvp_test_[a-z0-9_]+"))
            throw new IllegalStateException("Solo PostgreSQL desechable");
        p.add("spring.datasource.url",()->url);
        p.add("spring.datasource.username",()->System.getenv("MYCAR_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("MYCAR_TEST_PASSWORD"));
        p.add("mycar.auth.jwt-secret-base64",()->System.getenv("MYCAR_SESION_TEST_SIGNING_KEY"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RegisterAccount registro;
    @Autowired JdbcTemplate jdbc;
    final String password="FixtureIntegracion1!";
    String correo;
    long user;
    UUID instalacion;
    JsonNode tokens;
    @BeforeEach void cuentaYLogin() throws Exception {
        correo="integracion-"+UUID.randomUUID()+"@example.com";
        user=registro.register(new RegistrationCommand("Persona","Prueba",correo,LocalDate.of(1995,1,1),password,password,true,"FIXTURE-INTEGRACION-NO-LEGAL"));
        instalacion=UUID.randomUUID(); tokens=login(correo,instalacion);
    }
    JsonNode login(String email, UUID instalacion) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsBytes(
                Map.of("correo",email,"contrasena",password,"idInstalacion",instalacion))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String bearer() { return "Bearer "+tokens.get("accessToken").asText(); }
    JsonNode cuerpo(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }
    Map<String,Object> solicitud(UUID op) {
        long motor=jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION'",Long.class);
        long combustible=jdbc.queryForObject("SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA'",Long.class);
        // Placa global única, sin depender de catálogos de ejemplo ni de usuarios de otras suites.
        String placa="Z9Z"+String.format(Locale.ROOT,"%03d",user%1000);
        return Map.ofEntries(Map.entry("idOperacion",op),Map.entry("marca","Toyota"),Map.entry("modelo","Corolla"),
                Map.entry("usoVehiculoCodigo","PARTICULAR"),Map.entry("placa",placa),Map.entry("anioFabricacion",2020),
                Map.entry("color","Gris"),Map.entry("idTipoMotor",motor),Map.entry("idCombustible",combustible),Map.entry("kilometrajeInicial",58240));
    }
    @Test void loginRealAbreCatalogosCupoEIdentidadSinModificarContratoAuth() throws Exception {
        assertFalse(tokens.has("idUsuario"));
        mvc.perform(get("/api/v1/vehiculos/identidad?idUsuario=999999").header("X-Usuario-Id","999999").header("Authorization",bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.idUsuario").value(user));
        mvc.perform(get("/api/v1/vehiculos/catalogos").header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vehiculos/cupo-registro").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.puedeRegistrar").value(true));
    }
    @Test void registraListaRepiteYNoPermiteConsultarOperacionDeOtraCuenta() throws Exception {
        UUID op=UUID.randomUUID(); String entrada=json.writeValueAsString(solicitud(op));
        var primero=cuerpo(mvc.perform(post("/api/v1/vehiculos?idUsuario=999999").header("Authorization",bearer())
                .header("X-Usuario-Id","999999").contentType("application/json").content(entrada)).andExpect(status().isCreated()));
        long vehiculo=primero.get("idVehiculo").asLong();
        assertEquals(user,jdbc.queryForObject("SELECT id_usuario FROM mycar.vehiculo WHERE id_vehiculo=?",Long.class,vehiculo));
        assertEquals(primero,cuerpo(mvc.perform(post("/api/v1/vehiculos").header("Authorization",bearer()).contentType("application/json").content(entrada)).andExpect(status().isCreated())));
        mvc.perform(get("/api/v1/vehiculos").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.resumen.todos").value(1));
        mvc.perform(get("/api/v1/vehiculos/operaciones/"+op).header("Authorization",bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/vehiculos/cupo-registro").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.puedeRegistrar").value(false));
        String otra="otra-"+UUID.randomUUID()+"@example.com";
        registro.register(new RegistrationCommand("Otra","Persona",otra,LocalDate.of(1995,1,1),password,password,true,"FIXTURE-INTEGRACION-NO-LEGAL"));
        String ajeno="Bearer "+login(otra,UUID.randomUUID()).get("accessToken").asText();
        mvc.perform(get("/api/v1/vehiculos").header("Authorization",ajeno)).andExpect(status().isOk()).andExpect(jsonPath("$.resumen.todos").value(0));
        mvc.perform(get("/api/v1/vehiculos/operaciones/"+op).header("Authorization",ajeno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vehiculos/"+vehiculo).header("Authorization",bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idVehiculo").value(vehiculo));
        mvc.perform(get("/api/v1/vehiculos/"+vehiculo).header("Authorization",ajeno)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("VEHICULO_NO_ENCONTRADO"));
    }
    @Test void sinBearerOFalsificadoNingunaRutaExponeDatos() throws Exception {
        for(String ruta: List.of("", "/catalogos", "/cupo-registro", "/identidad", "/operaciones/"+UUID.randomUUID())) {
            mvc.perform(get("/api/v1/vehiculos"+ruta)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
            mvc.perform(get("/api/v1/vehiculos"+ruta).header("Authorization","Bearer falsificado")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/vehiculos").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }
    @Test void logoutRevocaVehiculosYNoRevocaOtraInstalacion() throws Exception {
        JsonNode otra=login(correo,UUID.randomUUID());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization",bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/vehiculos").header("Authorization",bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vehiculos").header("Authorization","Bearer "+otra.get("accessToken").asText())).andExpect(status().isOk());
    }
    @Test void sesionExpiradaOCuentaSuspendidaNoAccede() throws Exception {
        jdbc.update("UPDATE mycar.sesion SET expira_utc=clock_timestamp()-interval '1 second' WHERE id_sesion=?",tokens.get("idSesion").asLong());
        mvc.perform(get("/api/v1/vehiculos/identidad").header("Authorization",bearer())).andExpect(status().isUnauthorized());
        tokens=login(correo,instalacion);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA' WHERE id_usuario=?",user);
        mvc.perform(get("/api/v1/vehiculos").header("Authorization",bearer())).andExpect(status().isUnauthorized());
    }
    @Test void refreshRealRotaYConservaPropietario() throws Exception {
        String refresh=tokens.get("refreshToken").asText();
        tokens=cuerpo(mvc.perform(post("/api/v1/auth/renovar").contentType("application/json").content(json.writeValueAsString(
                Map.of("idInstalacion",instalacion,"refreshToken",refresh)))).andExpect(status().isOk()));
        mvc.perform(get("/api/v1/vehiculos/identidad").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.idUsuario").value(user));
        mvc.perform(post("/api/v1/auth/renovar").contentType("application/json").content(json.writeValueAsString(
                Map.of("idInstalacion",instalacion,"refreshToken",refresh)))).andExpect(status().isUnauthorized());
    }
    @Test void mismaSesionAbreMantenimientoYRegistraEnHistorialSinOtroLogin() throws Exception {
        JsonNode vehiculo=cuerpo(mvc.perform(post("/api/v1/vehiculos").header("Authorization",bearer())
                .contentType("application/json").content(json.writeValueAsString(solicitud(UUID.randomUUID())))).andExpect(status().isCreated()));
        long id=vehiculo.get("idVehiculo").asLong();
        JsonNode contexto=cuerpo(mvc.perform(get("/api/v1/rf17/contexto").header("Authorization",bearer())).andExpect(status().isOk()));
        JsonNode referencia=null;
        for(JsonNode v:contexto.get("vehiculos")) if(v.get("idVehiculo").asLong()==id) referencia=v;
        assertNotNull(referencia); assertTrue(contexto.get("registroDisponible").asBoolean());
        Map<String,Object> mantenimiento=Map.of("idOperacion",UUID.randomUUID(),"codigoTipoMantenimiento","MANTENIMIENTO_GENERAL",
                "fechaServicio",contexto.get("fechaServidor").asText(),"kmServicio",referencia.get("kmActual").asInt(),
                "origenKmServicio","LECTURA","idLecturaKmReferencia",referencia.get("idLecturaActual").asLong(),"repuestos",List.of());
        mvc.perform(post("/api/v1/rf17/vehiculos/"+id+"/mantenimientos").header("Authorization",bearer())
                .contentType("application/json").content(json.writeValueAsString(mantenimiento))).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/rf18/vehiculos/"+id+"/mantenimientos").header("Authorization",bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }
    @Test void logoutInvalidaTambienLosClientesDeMantenimientoYHistorial() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization",bearer())).andExpect(status().isNoContent());
        for(String ruta:List.of("/api/v1/rf17/contexto","/api/v1/rf18/contexto")) {
            mvc.perform(get(ruta).header("Authorization",bearer())).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        }
    }

}
