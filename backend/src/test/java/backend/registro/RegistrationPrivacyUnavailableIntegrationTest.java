package backend.registro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="MYCAR_RF01_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf01_test_[a-z0-9_]+")
@SpringBootTest(properties={"spring.config.import=optional:classpath:rf01-no-local-env.properties",
        "mycar.registro.privacidad.version-vigente=", "logging.level.org.springframework.security=ERROR"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RegistrationPrivacyUnavailableIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {RegistrationIntegrationTest.database(props);}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void absentLegalConfigurationReturns503AndWritesNothing() throws Exception {
        int before=jdbc.queryForObject("SELECT count(*) FROM mycar.usuario",Integer.class);
        mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content("""
                {"nombre":"Ana","apellido":"Pérez","correo":"sin-politica@example.com",
                 "fechaNacimiento":"1995-01-01","contrasena":"ClaveSegura1!",
                 "confirmacionContrasena":"ClaveSegura1!",
                 "consentimientoPrivacidad":{"aceptado":true,"version":"FIXTURE-NO-LEGAL"}}
                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("REGISTRO_NO_DISPONIBLE"));
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM mycar.usuario",Integer.class));
    }
}
