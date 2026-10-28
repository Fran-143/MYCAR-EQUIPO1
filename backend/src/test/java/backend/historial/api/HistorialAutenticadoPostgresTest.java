package backend.historial.api;

import backend.autenticacion.LoginCommand;
import backend.autenticacion.SessionAuthentication;
import backend.cierresesion.CloseSession;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import backend.mantenimiento.service.RegistroMantenimientoService;
import backend.registro.RegisterAccount;
import backend.registro.RegistrationCommand;
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
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Login, filtro JWT, PostgreSQL y RF17 reales; sin sustitutos de identidad ni tokens publicados. */
@EnabledIfSystemProperty(named = "mycar.rf18.tests", matches = "true")
@SpringBootTest(properties = {
        "spring.config.import=optional:classpath:rf18-sin-env.properties",
        "mycar.rf18.api-habilitada=true",
        "mycar.registro.privacidad.version-vigente=FIXTURE-NO-LEGAL",
        "logging.level.org.springframework.boot.autoconfigure.security=ERROR"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class HistorialAutenticadoPostgresTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegisterAccount registro;
    @Autowired SessionAuthentication sesiones;
    @Autowired CloseSession cierre;
    @Autowired RegistroMantenimientoService mantenimientos;
    private long usuario, vehiculo;
    private SessionAuthentication.Issued sesion;
    private static final String PASSWORD = "FixturePass1!";

    @DynamicPropertySource static void base(DynamicPropertyRegistry p) {
        String url = System.getenv("MYCAR_CATALOGO_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/mycar_mvp_test_rf(?:17|18)_[a-f0-9]{8}")) {
            throw new IllegalStateException("Solo se permite PostgreSQL temporal RF17/RF18");
        }
        p.add("spring.datasource.url", () -> url);
        p.add("spring.datasource.username", () -> System.getenv("MYCAR_CATALOGO_TEST_USER"));
        p.add("spring.datasource.password", () -> System.getenv("MYCAR_CATALOGO_TEST_PASSWORD"));
        byte[] clave = new byte[32]; new SecureRandom().nextBytes(clave);
        String secreto = Base64.getEncoder().encodeToString(clave);
        p.add("mycar.auth.jwt-secret-base64", () -> secreto);
        p.add("mycar.auth.access-ttl", () -> "PT5M");
        p.add("mycar.auth.session-ttl", () -> "P1D");
    }

    @BeforeEach void preparar() {
        assertTrue(jdbc.queryForObject("SELECT current_database()", String.class).matches("mycar_mvp_test_rf(?:17|18)_[a-f0-9]{8}"));
        String email = "rf18-auth-" + UUID.randomUUID() + "@example.test";
        usuario = registro.register(new RegistrationCommand("Prueba", "Historial", email,
                LocalDate.of(1995,1,1), PASSWORD, PASSWORD, true, "FIXTURE-NO-LEGAL"));
        sesion = sesiones.login(new LoginCommand(email, PASSWORD, UUID.randomUUID()));
        vehiculo = jdbc.queryForObject("""
                INSERT INTO mycar.vehiculo (id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor)
                VALUES (?,?,'Toyota','Corolla','PARTICULAR',2020,'Azul',
                    (SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO')) RETURNING id_vehiculo
                """, Long.class, usuario, "H8H" + String.format("%03d", usuario % 1000));
        mantenimientos.registrar(usuario, new SolicitudRegistroMantenimiento(UUID.randomUUID(), vehiculo,
                new DatosRegistroMantenimiento("MANTENIMIENTO_GENERAL", LocalDate.now(ZoneId.of("America/Lima")).minusDays(1),
                        21000, OrigenKmServicio.HISTORICO_MANUAL, null, null, null, null, null), null, List.of()));
    }

    @Test void tokenRealConsultaLoGuardadoPorRF17YNoEscribeAlLeer() throws Exception {
        long operaciones = jdbc.queryForObject("SELECT count(*) FROM mycar.operacion_idempotente", Long.class);
        mvc.perform(get("/api/v1/rf18/contexto").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.vehiculos[0].idVehiculo").value(vehiculo));
        mvc.perform(get("/api/v1/rf18/vehiculos/" + vehiculo + "/mantenimientos").header("Authorization", bearer()).param("idUsuario", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.mantenimientos[0].kmServicio").value(21000));
        assertEquals(operaciones, jdbc.queryForObject("SELECT count(*) FROM mycar.operacion_idempotente", Long.class));
    }

    @Test void sinTokenOTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/api/v1/rf18/contexto")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/rf18/contexto").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
    }

    @Test void otraCuentaNoPuedeLeerElVehiculo() throws Exception {
        String email = "rf18-otro-" + UUID.randomUUID() + "@example.test";
        registro.register(new RegistrationCommand("Otra", "Cuenta", email, LocalDate.of(1995,1,1), PASSWORD, PASSWORD, true, "FIXTURE-NO-LEGAL"));
        var otra = sesiones.login(new LoginCommand(email, PASSWORD, UUID.randomUUID()));
        mvc.perform(get("/api/v1/rf18/vehiculos/" + vehiculo + "/mantenimientos")
                .header("Authorization", "Bearer " + otra.accessToken())).andExpect(status().isNotFound());
    }

    @Test void logoutRealRevocaAccesoAlHistorial() throws Exception {
        cierre.close(sesion.accessToken());
        mvc.perform(get("/api/v1/rf18/contexto").header("Authorization", bearer())).andExpect(status().isUnauthorized());
    }

    @Test void cuentaSuspendidaNoConsultaAunqueConserveJwt() throws Exception {
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA' WHERE id_usuario=?", usuario);
        mvc.perform(get("/api/v1/rf18/contexto").header("Authorization", bearer())).andExpect(status().isUnauthorized());
    }

    @Test void noAdmiteEscriturasEnLaApiDeHistorial() throws Exception {
        mvc.perform(post("/api/v1/rf18/vehiculos/" + vehiculo + "/mantenimientos").header("Authorization", bearer()))
                .andExpect(status().isForbidden());
    }

    private String bearer() { return "Bearer " + sesion.accessToken(); }
}
