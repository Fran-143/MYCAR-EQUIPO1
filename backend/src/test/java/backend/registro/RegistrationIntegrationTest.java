package backend.registro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="MYCAR_RF01_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf01_test_[a-z0-9_]+")
@SpringBootTest(properties={"spring.config.import=optional:classpath:rf01-no-local-env.properties",
        "mycar.registro.privacidad.version-vigente=FIXTURE-NO-LEGAL",
        "logging.level.org.springframework.security=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RegistrationIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry props) {
        String url=System.getenv("MYCAR_RF01_TEST_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf01_test_[a-z0-9_]+"))
            throw new IllegalStateException("Solo PostgreSQL desechable de RF01.");
        props.add("spring.datasource.url",()->url);
        props.add("spring.datasource.username",()->System.getenv("MYCAR_RF01_TEST_USER"));
        props.add("spring.datasource.password",()->System.getenv("MYCAR_RF01_TEST_PASSWORD"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RegisterAccount register;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void resetOnlyDisposableDatabase() {
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).startsWith("mycar_rf01_test_"));
        for(String table: new String[]{"preferencia_notificacion","config_recordatorio_km_usuario","suscripcion","usuario"})
            jdbc.update("DELETE FROM mycar."+table);
        jdbc.update("UPDATE mycar.plan SET activo=TRUE WHERE codigo='FREE'");
    }

    Map<String,Object> body(String email) {
        Map<String,Object> b=new HashMap<>();
        b.put("nombre","  Ana   María  "); b.put("apellido","D'Ávila"); b.put("correo",email);
        b.put("fechaNacimiento","1995-01-01"); b.put("contrasena","ClaveSegura1!");
        b.put("confirmacionContrasena","ClaveSegura1!");
        b.put("consentimientoPrivacidad",Map.of("aceptado",true,"version","FIXTURE-NO-LEGAL"));
        return b;
    }

    @Test void publicRegistrationPersistsAllDefaultsWithArgon2AndNoSession() throws Exception {
        jdbc.update("DELETE FROM mycar.plan WHERE codigo='FREE'");
        jdbc.update("INSERT INTO mycar.plan(codigo,nombre,max_vehiculos_registrados,activo) VALUES ('FREE','Gratis',1,TRUE)");
        assertNotEquals(1L,jdbc.queryForObject("SELECT id_plan FROM mycar.plan WHERE codigo='FREE'",Long.class));
        Instant before=Instant.now();
        var response=mvc.perform(post("/api/v1/auth/registro").contentType("application/json")
                .content(json.writeValueAsBytes(body("  Elsa.Test+Curso@Example.COM  "))))
                .andExpect(status().isCreated()).andExpect(content().json("{\"resultado\":\"CUENTA_CREADA\",\"destino\":\"INICIO_SESION\"}"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(response.getRequest().getSession(false));
        var user=jdbc.queryForMap("SELECT * FROM mycar.usuario");
        assertEquals("Ana María",user.get("nombre")); assertEquals("D'Ávila",user.get("apellido"));
        assertEquals("elsa.test+curso@example.com",user.get("correo"));
        assertEquals("ACTIVA",user.get("estado_cuenta")); assertEquals("USUARIO",user.get("codigo_rol"));
        String hash=(String)user.get("password_hash");
        assertTrue(hash.startsWith("$argon2id$")); assertTrue(encoder.matches("ClaveSegura1!",hash));
        Instant accepted=jdbc.queryForObject("SELECT consentimiento_privacidad_fecha_utc FROM mycar.usuario",java.sql.Timestamp.class).toInstant();
        assertFalse(accepted.isBefore(before)); assertFalse(accepted.isAfter(Instant.now()));
        assertEquals("FIXTURE-NO-LEGAL",user.get("consentimiento_privacidad_version"));
        assertEquals("FREE:ACTIVA",jdbc.queryForObject("SELECT p.codigo||':'||s.estado FROM mycar.suscripcion s JOIN mycar.plan p USING(id_plan) WHERE s.fin_utc IS NULL",String.class));
        assertEquals("PAUSADO",jdbc.queryForObject("SELECT estado_recordatorios FROM mycar.config_recordatorio_km_usuario",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.preferencia_notificacion WHERE mantenimiento_on AND soat_on AND kilometraje_on AND permiso_dispositivo='NO_SOLICITADO'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mycar.sesion",Integer.class));
        assertFalse(response.getResponse().getContentAsString().contains(hash));
    }

    @ParameterizedTest @ValueSource(strings={"ACTIVA","SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void duplicateEmailIsGenericAcrossAllStates(String state) throws Exception {
        register.register(RegistrationValidatorTest.command("Ana",LocalDate.of(1995,1,1),"ClaveSegura1!"));
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta=?",state);
        mvc.perform(post("/api/v1/auth/registro").contentType("application/json")
                .content(json.writeValueAsBytes(body("ELSA.TEST+CURSO@EXAMPLE.COM"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CORREO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("Correo no disponible."));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.usuario",Integer.class));
    }

    @ParameterizedTest @ValueSource(strings={"suscripcion","config_recordatorio_km_usuario","preferencia_notificacion"})
    void failureAtAnyInitializationRollsBackAllFourRows(String table) throws Exception {
        // Fault injection only in this guarded disposable DB; not a Flyway migration.
        jdbc.execute("ALTER TABLE mycar."+table+" ADD CONSTRAINT rf01_test_failure CHECK (FALSE) NOT VALID");
        try {
            mvc.perform(post("/api/v1/auth/registro").contentType("application/json")
                    .content(json.writeValueAsBytes(body("rollback@example.com"))))
                    .andExpect(status().isServiceUnavailable());
            assertNoAccounts();
        } finally { jdbc.execute("ALTER TABLE mycar."+table+" DROP CONSTRAINT rf01_test_failure"); }
    }

    @Test void inactiveFreePreventsPartialAccount() throws Exception {
        jdbc.update("UPDATE mycar.plan SET activo=FALSE WHERE codigo='FREE'");
        mvc.perform(post("/api/v1/auth/registro").contentType("application/json")
                .content(json.writeValueAsBytes(body("nofree@example.com"))))
                .andExpect(status().isServiceUnavailable());
        assertNoAccounts();
    }

    @Test void simultaneousSameEmailCreatesExactlyOneCompleteAccount() throws Exception {
        var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<String> attempt=()-> {
                barrier.await(10,TimeUnit.SECONDS);
                try {register.register(RegistrationValidatorTest.command("Ana",LocalDate.of(1995,1,1),"ClaveSegura1!"));return "created";}
                catch(RegistrationException ex){return ex.reason().name();}
            };
            var a=pool.submit(attempt); var b=pool.submit(attempt);
            assertEquals(java.util.Set.of("created","EMAIL_UNAVAILABLE"),java.util.Set.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS)));
        }
        for(String table:new String[]{"usuario","suscripcion","preferencia_notificacion","config_recordatorio_km_usuario"})
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar."+table,Integer.class));
    }

    @Test void malformedDateAndInvalidConsentNeverPersistOrEchoSecrets() throws Exception {
        var b=body("invalid@example.com"); b.put("fechaNacimiento","2026-02-30");
        mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(json.writeValueAsBytes(b)))
                .andExpect(status().isBadRequest());
        b=body("invalid@example.com"); b.put("consentimientoPrivacidad",Map.of("aceptado",false,"version","FIXTURE-NO-LEGAL"));
        String text=mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(json.writeValueAsBytes(b)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertFalse(text.contains("ClaveSegura1!"));
        b.put("consentimientoPrivacidad",Map.of("aceptado",true,"version","DESACTUALIZADA-FIXTURE"));
        mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(json.writeValueAsBytes(b)))
                .andExpect(status().isBadRequest());
        assertNoAccounts();
    }

    @Test void directRequestsRevalidateNamePasswordEmailAndAge() throws Exception {
        for(String field:new String[]{"nombre","apellido","correo","contrasena","confirmacionContrasena","fechaNacimiento"}) {
            var b=body("valid@example.com");
            b.put(field,switch(field){case "fechaNacimiento" -> LocalDate.now(ZoneId.of("America/Lima")).minusYears(17).toString(); case "correo" -> "sin-arroba"; default -> "1";});
            mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(json.writeValueAsBytes(b)))
                    .andExpect(status().isBadRequest());
        }
        assertNoAccounts();
    }

    @Test void unrelatedRoutesRemainProtectedAndHealthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/ruta-no-autorizada")).andExpect(status().isForbidden());
    }

    private void assertNoAccounts() {
        for(String table:new String[]{"usuario","suscripcion","preferencia_notificacion","config_recordatorio_km_usuario"})
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mycar."+table,Integer.class));
    }
}
