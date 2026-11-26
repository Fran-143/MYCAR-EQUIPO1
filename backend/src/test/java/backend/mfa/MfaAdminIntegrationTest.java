package backend.mfa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import backend.registro.RegisterAccount;
import backend.registro.RegistrationCommand;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="MYCAR_RF02_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf02_test_[a-z0-9_]+")
@SpringBootTest(properties={"spring.config.import=optional:classpath:rf02-no-local-env.properties",
        "mycar.registro.privacidad.version-vigente=FIXTURE-NO-LEGAL",
        "mycar.mfa.enabled=true",
        "logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class MfaAdminIntegrationTest {
    static final String EMAIL="admin.mfa+curso@example.com";
    static final String PASSWORD="FixturePass1!";
    static final String MFA_KEY=Base64.getEncoder().encodeToString(new byte[]{
            1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32});

    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        String url=System.getenv("MYCAR_RF02_TEST_URL");
        p.add("spring.datasource.url",()->url);
        p.add("spring.datasource.username",()->System.getenv("MYCAR_RF02_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("MYCAR_RF02_TEST_PASSWORD"));
        p.add("mycar.auth.jwt-secret-base64",()->System.getenv("MYCAR_RF02_TEST_SIGNING_KEY"));
        p.add("mycar.auth.access-ttl",()->"PT5M");
        p.add("mycar.auth.session-ttl",()->"P1D");
        p.add("mycar.mfa.aes-key",()->MFA_KEY);
        p.add("mycar.mfa.key-id",()->"v1");
        p.add("mycar.mfa.bootstrap-secret",()->"local-bootstrap-secret");
        p.add("mycar.mfa.bootstrap-enabled",()->"true");
        p.add("mycar.auth.login-auto-alta",()->"true");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegisterAccount registration;
    @Autowired @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords;

    @BeforeEach void clean(){
        for(String table:new String[]{"mfa_auditoria","mfa_codigo_recuperacion","mfa_desafio","mfa_configuracion","sesion","intento_login_fallido","preferencia_notificacion","config_recordatorio_km_usuario","suscripcion","usuario"})
            jdbc.update("DELETE FROM mycar."+table);
        registration.register(new RegistrationCommand("Ada","Admin",EMAIL,LocalDate.of(1990,1,1),PASSWORD,PASSWORD,true,"FIXTURE-NO-LEGAL"));
    }

    @Test void ea01UsuarioNormalSinTotp()throws Exception{
        String userEmail="user.normal+curso@example.com";
        registration.register(new RegistrationCommand("User","Normal",userEmail,LocalDate.of(1991,1,1),PASSWORD,PASSWORD,true,"FIXTURE-NO-LEGAL"));
        var result=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",userEmail,"contrasena",PASSWORD,"idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isOk()).andReturn();
        JsonNode n=json.readTree(result.getResponse().getContentAsString());
        assertEquals("AUTENTICADO",n.get("estado").asText());
        assertEquals("USUARIO",n.get("alcance").asText());
    }

    @Test void ea02CredencialesInvalidas()throws Exception{
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena","wrong","idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test void ea03AdminSinMfaExigeEnrollYNoPanel()throws Exception{
        mvc.perform(post("/api/v1/ops/admin-bootstrap").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"secretoBootstrap","local-bootstrap-secret"))))
                .andExpect(status().isOk());
        var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena",PASSWORD,"idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isOk()).andReturn();
        JsonNode n=json.readTree(login.getResponse().getContentAsString());
        assertEquals("MFA_ENROLL_REQUERIDO",n.get("estado").asText());
        assertEquals("MFA_CHALLENGE",n.get("alcance").asText());
        String access=n.get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/hub").header("Authorization","Bearer "+access))
                .andExpect(status().isForbidden());
    }

    @Test void ea04a12EnrollVerifyRecoveryYHub()throws Exception{
        mvc.perform(post("/api/v1/ops/admin-bootstrap").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"secretoBootstrap","local-bootstrap-secret"))))
                .andExpect(status().isOk());
        UUID installation=UUID.randomUUID();
        JsonNode login=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena",PASSWORD,"idInstalacion",installation))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String challenge=login.get("accessToken").asText();
        UUID desafioId=UUID.fromString(login.get("desafioId").asText());

        JsonNode enroll=json.readTree(mvc.perform(post("/api/v1/auth/mfa/enroll/iniciar")
                .header("Authorization","Bearer "+challenge))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String secret=enroll.get("secretoManual").asText();
        String code=currentTotp(secret);

        JsonNode recovery=json.readTree(mvc.perform(post("/api/v1/auth/mfa/enroll/confirmar")
                .header("Authorization","Bearer "+challenge)
                .contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("codigo",code))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("MFA_RECOVERY_EMITIDO",recovery.get("estado").asText());
        assertTrue(recovery.get("codigosRecuperacion").isArray());
        String recoveryCode=recovery.get("codigosRecuperacion").get(0).asText();

        JsonNode admin=json.readTree(mvc.perform(post("/api/v1/auth/mfa/enroll/confirmar-respaldo")
                .header("Authorization","Bearer "+challenge))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("AUTENTICADO",admin.get("estado").asText());
        assertEquals("ADMIN",admin.get("alcance").asText());
        String adminToken=admin.get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/hub").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secciones.length()").value(4));

        // Logout + login habitual + TOTP
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+adminToken)).andExpect(status().isNoContent());
        JsonNode login2=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena",PASSWORD,"idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("MFA_REQUERIDO",login2.get("estado").asText());
        String challenge2=login2.get("accessToken").asText();
        UUID desafio2=UUID.fromString(login2.get("desafioId").asText());
        // EA04 código malo
        mvc.perform(post("/api/v1/auth/mfa/verificar").header("Authorization","Bearer "+challenge2)
                .contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("desafioId",desafio2,"codigo","000000"))))
                .andExpect(status().isUnauthorized());
        // TOTP ok
        JsonNode ok=json.readTree(mvc.perform(post("/api/v1/auth/mfa/verificar").header("Authorization","Bearer "+challenge2)
                .contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("desafioId",desafio2,"codigo",currentTotp(secret)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("ADMIN",ok.get("alcance").asText());

        // Recovery → reenroll sin panel
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+ok.get("accessToken").asText()));
        JsonNode login3=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena",PASSWORD,"idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String challenge3=login3.get("accessToken").asText();
        UUID desafio3=UUID.fromString(login3.get("desafioId").asText());
        JsonNode reenroll=json.readTree(mvc.perform(post("/api/v1/auth/mfa/recuperar")
                .header("Authorization","Bearer "+challenge3)
                .contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("desafioId",desafio3,"codigoRespaldo",recoveryCode))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("MFA_REENROLL_REQUERIDO",reenroll.get("estado").asText());
        assertEquals("MFA_REENROLL",reenroll.get("alcance").asText());
        mvc.perform(get("/api/v1/admin/hub").header("Authorization","Bearer "+reenroll.get("accessToken").asText()))
                .andExpect(status().isForbidden());
    }

    @Test void ea11SinClaveMfaFailClosed()throws Exception{
        // Crypto bean already configured in this context; assert unavailable path via dedicated crypto.
        MfaCrypto disabled=MfaCrypto.deshabilitado();
        assertThrows(MfaFailure.class,disabled::exigir);
    }

    private static String currentTotp(String secretBase32)throws Exception{
        TotpService svc=new TotpService();
        // Use same decode path as service by generating via eatthepath with decoded key from verify helper:
        TimeBasedOneTimePasswordGenerator gen=new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30),6);
        byte[] key=decode32(secretBase32);
        SecretKey sk=new SecretKeySpec(key,gen.getAlgorithm());
        return String.format("%06d",gen.generateOneTimePassword(sk,Instant.now()));
    }
    private static byte[] decode32(String encoded){
        String s=encoded.trim().toUpperCase().replace("=","");
        final String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] out=new byte[s.length()*5/8];
        int buffer=0,bitsLeft=0,index=0;
        for(int i=0;i<s.length();i++){
            int val=alphabet.indexOf(s.charAt(i));
            buffer=(buffer<<5)|val;bitsLeft+=5;
            if(bitsLeft>=8){out[index++]=(byte)((buffer>>(bitsLeft-8))&0xff);bitsLeft-=8;}
        }
        if(index!=out.length){byte[] t=new byte[index];System.arraycopy(out,0,t,0,index);return t;}
        return out;
    }
}
