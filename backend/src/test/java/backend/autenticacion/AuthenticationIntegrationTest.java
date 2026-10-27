package backend.autenticacion;

import backend.registro.RegisterAccount;
import backend.registro.RegistrationCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="MYCAR_RF02_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf02_test_[a-z0-9_]+")
@SpringBootTest(properties={"spring.config.import=optional:classpath:rf02-no-local-env.properties",
        "mycar.registro.privacidad.version-vigente=FIXTURE-NO-LEGAL",
        "logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationIntegrationTest {
    static final String EMAIL="elsa.test+curso@example.com";
    static final String PASSWORD="FixturePass1!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        String url=System.getenv("MYCAR_RF02_TEST_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf02_test_[a-z0-9_]+"))throw new IllegalStateException("Disposable RF02 database required");
        p.add("spring.datasource.url",()->url);
        p.add("spring.datasource.username",()->System.getenv("MYCAR_RF02_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("MYCAR_RF02_TEST_PASSWORD"));
        p.add("mycar.auth.jwt-secret-base64",()->System.getenv("MYCAR_RF02_TEST_SIGNING_KEY"));
        p.add("mycar.auth.access-ttl",()->"PT5M"); // Technical test fixtures, not production defaults.
        p.add("mycar.auth.session-ttl",()->"P1D");
    }
    @Autowired RegisterAccount registration;
    @Autowired SessionAuthentication auth;
    @Autowired SessionCredentials credentials;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LoginValidator validator;
    @Autowired AuthenticationStore store;
    @Autowired PlatformTransactionManager transactions;
    @Autowired @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords;
    long user;

    record Tokens(long sid,String access,String refresh,Instant expires){
        @Override public String toString(){return "TestTokens[REDACTED]";}
    }
    @BeforeEach void prepareDisposableFixtures(){
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).startsWith("mycar_rf02_test_"));
        for(String table:new String[]{"sesion","intento_login_fallido","preferencia_notificacion","config_recordatorio_km_usuario","suscripcion","usuario"})jdbc.update("DELETE FROM mycar."+table);
        user=registration.register(new RegistrationCommand("Elsa","Pérez",EMAIL,LocalDate.of(1995,1,1),PASSWORD,PASSWORD,true,"FIXTURE-NO-LEGAL"));
    }
    LoginCommand command(UUID installation,String password){return new LoginCommand(EMAIL,password,installation);}
    Tokens tokens(SessionAuthentication.Issued issued){return new Tokens(issued.idSesion(),issued.accessToken(),issued.refreshToken(),issued.sesionExpiraUtc());}
    Tokens login(UUID installation)throws Exception{
        var result=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo","  ELSA.TEST+CURSO@EXAMPLE.COM  ","contrasena",PASSWORD,"idInstalacion",installation))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
        JsonNode n=json.readTree(result.getResponse().getContentAsString());
        assertEquals("AUTENTICADO",n.get("estado").asText());
        return new Tokens(n.get("idSesion").asLong(),n.get("accessToken").asText(),n.get("refreshToken").asText(),Instant.parse(n.get("sesionExpiraUtc").asText()));
    }
    void denied(UUID installation){assertEquals(AuthenticationFailure.Reason.INVALID_CREDENTIALS,assertThrows(AuthenticationFailure.class,()->auth.login(command(installation,"incorrecta"))).reason());}
    void valid(Tokens tokens)throws Exception{mvc.perform(get("/api/v1/auth/sesion").header("Authorization","Bearer "+tokens.access())).andExpect(status().isOk()).andExpect(jsonPath("$.idSesion").value(tokens.sid()));}
    void invalid(Tokens tokens)throws Exception{mvc.perform(get("/api/v1/auth/sesion").header("Authorization","Bearer "+tokens.access())).andExpect(status().isUnauthorized());}
    int count(String table){return jdbc.queryForObject("SELECT count(*) FROM mycar."+table,Integer.class);}
    String state(long id){return jdbc.queryForObject("SELECT estado_sesion FROM mycar.sesion WHERE id_sesion=?",String.class,id);}

    @Test void successUsesArgon2FromRf01AndStoresOnlyRefreshHash(CapturedOutput output)throws Exception{
        UUID installation=UUID.randomUUID();Tokens t=login(installation);valid(t);
        assertEquals(SessionCredentials.hashRefresh(t.refresh()),jdbc.queryForObject("SELECT refresh_token_hash FROM mycar.sesion",String.class));
        assertEquals(1,count("sesion"));assertEquals(0,count("intento_login_fallido"));
        assertFalse(output.getAll().contains(PASSWORD));assertFalse(output.getAll().contains(t.access()));assertFalse(output.getAll().contains(t.refresh()));
    }
    @ParameterizedTest @ValueSource(strings={"ACTIVA","SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void wrongPasswordAndUnknownEmailHaveIdenticalPublicResponse(String state)throws Exception{
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta=? WHERE id_usuario=?",state,user);
        String a=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena","incorrecta","idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String b=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("correo","inexistente@example.com","contrasena","incorrecta","idInstalacion",UUID.randomUUID()))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(a,b);assertEquals("ACTIVA".equals(state)?1:0,count("intento_login_fallido"));assertEquals(0,count("sesion"));
    }
    @Test void threeFailuresCommitAndRestrictionNeitherExtendsNorKillsExistingSession()throws Exception{
        UUID installation=UUID.randomUUID();Tokens existing=login(installation);
        for(int i=0;i<3;i++)denied(UUID.randomUUID());
        Instant deadline=jdbc.queryForObject("SELECT login_bloqueo_hasta_utc FROM mycar.usuario",java.sql.Timestamp.class).toInstant();
        assertTrue(deadline.isAfter(Instant.now().plusSeconds(590)));assertEquals(3,count("intento_login_fallido"));
        assertThrows(AuthenticationFailure.class,()->auth.login(command(installation,PASSWORD)));
        assertEquals(deadline,jdbc.queryForObject("SELECT login_bloqueo_hasta_utc FROM mycar.usuario",java.sql.Timestamp.class).toInstant());
        assertEquals(3,count("intento_login_fallido"));assertEquals("ACTIVA",jdbc.queryForObject("SELECT estado_cuenta FROM mycar.usuario",String.class));
        valid(existing);
        Tokens renewed=tokens(auth.renew(installation,existing.refresh()));valid(renewed);
        assertEquals(3,count("intento_login_fallido"));
    }
    @Test void expiredRestrictionStartsNewCounterWithoutDeletingEvidence(){
        for(int i=0;i<3;i++)denied(UUID.randomUUID());
        jdbc.update("UPDATE mycar.usuario SET login_bloqueo_hasta_utc=clock_timestamp()-interval '1 second'");
        denied(UUID.randomUUID());
        assertNull(jdbc.queryForObject("SELECT login_bloqueo_hasta_utc FROM mycar.usuario",java.sql.Timestamp.class));
        assertEquals(4,count("intento_login_fallido"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.intento_login_fallido i JOIN mycar.usuario u USING(id_usuario) WHERE i.ocurrido_utc>=u.login_fallos_desde_utc",Integer.class));
    }
    @Test void oldAttemptsOutsideSlidingWindowDoNotBlock(){
        jdbc.update("INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES (?,clock_timestamp()-interval '11 minutes'),(?,clock_timestamp()-interval '12 minutes')",user,user);
        denied(UUID.randomUUID());
        assertEquals(3,count("intento_login_fallido"));
        assertNull(jdbc.queryForObject("SELECT login_bloqueo_hasta_utc FROM mycar.usuario",java.sql.Timestamp.class));
    }
    @Test void successfulLoginResetsCurrentFailuresButPreservesHistory(){
        denied(UUID.randomUUID());denied(UUID.randomUUID());
        auth.login(command(UUID.randomUUID(),PASSWORD));
        denied(UUID.randomUUID());
        assertEquals(3,count("intento_login_fallido"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.intento_login_fallido i JOIN mycar.usuario u USING(id_usuario) WHERE i.ocurrido_utc>=u.login_fallos_desde_utc",Integer.class));
    }
    @Test void differentInstallationsRemainValid()throws Exception{
        Tokens a=login(UUID.randomUUID()),b=login(UUID.randomUUID());valid(a);valid(b);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM mycar.sesion WHERE estado_sesion='VIGENTE'",Integer.class));
    }
    @Test void sameInstallationReplacesOnlyItsPreviousSession()throws Exception{
        UUID installation=UUID.randomUUID();Tokens a=login(installation),other=login(UUID.randomUUID()),b=login(installation);
        assertNotEquals(a.sid(),b.sid());assertEquals("REVOCADA",state(a.sid()));
        invalid(a);valid(b);valid(other);
        assertThrows(AuthenticationFailure.class,()->auth.renew(installation,a.refresh()));
    }
    @Test void refreshRotatesWithoutExtendingAbsoluteDeadlineAndCannotBeReplayed()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);
        var result=mvc.perform(post("/api/v1/auth/renovar").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("idInstalacion",installation,"refreshToken",first.refresh()))))
                .andExpect(status().isOk()).andReturn();
        var body=json.readTree(result.getResponse().getContentAsString());
        assertEquals(first.sid(),body.get("idSesion").asLong());
        assertEquals(first.expires(),Instant.parse(body.get("sesionExpiraUtc").asText()));
        assertFalse(first.refresh().equals(body.get("refreshToken").asText()));
        assertThrows(AuthenticationFailure.class,()->auth.renew(installation,first.refresh()));
        assertEquals(1,count("sesion"));
    }
    @Test void expiredAccessCanRenewWhileSessionAuthorizationRemainsValid()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);
        var old=credentials.issue(user,first.sid(),installation,Instant.now().minusSeconds(600),first.expires());
        invalid(new Tokens(first.sid(),old.value(),first.refresh(),first.expires()));
        valid(tokens(auth.renew(installation,first.refresh())));
    }
    @Test void expiredSessionCannotAccessOrRenewAndCanBeReplaced()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);
        jdbc.update("UPDATE mycar.sesion SET expira_utc=clock_timestamp()-interval '1 second'");
        invalid(first);assertThrows(AuthenticationFailure.class,()->auth.renew(installation,first.refresh()));
        Tokens next=login(installation);valid(next);assertEquals("EXPIRADA",state(first.sid()));
    }
    @Test void installationIdAloneAndMismatchedJwtIdentityNeverAuthorize()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);
        assertThrows(AuthenticationFailure.class,()->auth.renew(UUID.randomUUID(),first.refresh()));
        var wrong=credentials.issue(user,first.sid(),UUID.randomUUID(),Instant.now(),first.expires());
        invalid(new Tokens(first.sid(),wrong.value(),first.refresh(),first.expires()));
        var wrongUser=credentials.issue(user+999,first.sid(),installation,Instant.now(),first.expires());
        invalid(new Tokens(first.sid(),wrongUser.value(),first.refresh(),first.expires()));valid(first);
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void protectedSessionsAndRenewalStay401WhileCorrectPasswordLoginReturns403(String state)throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta=?",state);
        invalid(first);assertThrows(AuthenticationFailure.class,()->auth.renew(installation,first.refresh()));
        assertEquals(AuthenticationFailure.Reason.ACCOUNT_NOT_ENABLED,assertThrows(AuthenticationFailure.class,()->auth.login(command(installation,PASSWORD))).reason());
        assertNonActive403(installation);
        mvc.perform(post("/api/v1/auth/renovar").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("idInstalacion",installation,"refreshToken",first.refresh()))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
        assertEquals("VIGENTE",state(first.sid()));
        assertEquals(0,count("intento_login_fallido"));assertEquals(1,count("sesion"));
    }
    void assertNonActive403(UUID installation)throws Exception{
        String body=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena",PASSWORD,"idInstalacion",installation))))
                .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree("{\"codigo\":\"CUENTA_NO_HABILITADA\",\"mensaje\":\"La cuenta no está habilitada para iniciar sesión.\",\"erroresPorCampo\":{}}"),json.readTree(body));
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void nonActiveLiveRestrictionRemainsGenericWithoutExtendingOrWriting(String state)throws Exception{
        jdbc.update("INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES (?,clock_timestamp()-interval '2 minutes')",user);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta=?,login_bloqueo_hasta_utc=clock_timestamp()+interval '5 minutes',login_fallos_desde_utc=clock_timestamp()-interval '3 minutes' WHERE id_usuario=?",state,user);
        var before=jdbc.queryForMap("SELECT login_bloqueo_hasta_utc,login_fallos_desde_utc FROM mycar.usuario WHERE id_usuario=?",user);
        for(String password:new String[]{PASSWORD,"incorrecta"}){
            String response=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content(json.writeValueAsBytes(Map.of("correo",EMAIL,"contrasena",password,"idInstalacion",UUID.randomUUID()))))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
            String unknown=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content(json.writeValueAsBytes(Map.of("correo","inexistente@example.com","contrasena",password,"idInstalacion",UUID.randomUUID()))))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
            assertEquals(unknown,response);
        }
        assertEquals(before,jdbc.queryForMap("SELECT login_bloqueo_hasta_utc,login_fallos_desde_utc FROM mycar.usuario WHERE id_usuario=?",user));
        assertEquals(1,count("intento_login_fallido"));assertEquals(0,count("sesion"));
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void nonActiveExpiredRestrictionPreservesHistoryAndAllowsPasswordDecision(String state)throws Exception{
        jdbc.update("INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES (?,clock_timestamp()-interval '20 minutes')",user);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta=?,login_bloqueo_hasta_utc=clock_timestamp()-interval '1 second',login_fallos_desde_utc=clock_timestamp()-interval '30 minutes' WHERE id_usuario=?",state,user);
        var before=jdbc.queryForMap("SELECT login_bloqueo_hasta_utc,login_fallos_desde_utc FROM mycar.usuario WHERE id_usuario=?",user);
        denied(UUID.randomUUID());assertNonActive403(UUID.randomUUID());
        assertEquals(before,jdbc.queryForMap("SELECT login_bloqueo_hasta_utc,login_fallos_desde_utc FROM mycar.usuario WHERE id_usuario=?",user));
        assertEquals(1,count("intento_login_fallido"));assertEquals(0,count("sesion"));
    }
    @Test void concurrentFailuresAcrossDevicesProduceOneRestriction(){
        var barrier=new CyclicBarrier(3);
        try(var pool=Executors.newFixedThreadPool(3)){
            Callable<Boolean> attempt=()->{barrier.await(10,TimeUnit.SECONDS);denied(UUID.randomUUID());return true;};
            var a=pool.submit(attempt);var b=pool.submit(attempt);var c=pool.submit(attempt);
            assertTrue(a.get(30,TimeUnit.SECONDS));assertTrue(b.get(30,TimeUnit.SECONDS));assertTrue(c.get(30,TimeUnit.SECONDS));
        }catch(Exception ex){throw new AssertionError("Concurrent login failed",ex);}
        assertEquals(3,count("intento_login_fallido"));
        assertNotNull(jdbc.queryForObject("SELECT login_bloqueo_hasta_utc FROM mycar.usuario",java.sql.Timestamp.class));
    }
    @Test void concurrentLoginsSameInstallationLeaveOneCurrentSession()throws Exception{
        UUID installation=UUID.randomUUID();var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<SessionAuthentication.Issued> attempt=()->{barrier.await(10,TimeUnit.SECONDS);return auth.login(command(installation,PASSWORD));};
            var a=pool.submit(attempt);var b=pool.submit(attempt);
            var first=a.get(30,TimeUnit.SECONDS);var second=b.get(30,TimeUnit.SECONDS);
            assertNotEquals(first.idSesion(),second.idSesion());
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.sesion WHERE estado_sesion='VIGENTE'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.sesion WHERE estado_sesion='REVOCADA'",Integer.class));
    }
    @Test void concurrentRefreshConsumesCredentialOnlyOnce()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> attempt=()->{barrier.await(10,TimeUnit.SECONDS);try{auth.renew(installation,first.refresh());return true;}catch(AuthenticationFailure ex){return false;}};
            var a=pool.submit(attempt);var b=pool.submit(attempt);
            assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
        assertEquals(1,count("sesion"));
    }
    @Test void failedSessionCreationRollsBackReplacementAndCounterReset()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);denied(UUID.randomUUID());
        Instant marker=jdbc.queryForObject("SELECT login_fallos_desde_utc FROM mycar.usuario",java.sql.Timestamp.class).toInstant();
        jdbc.execute("ALTER TABLE mycar.sesion ADD CONSTRAINT rf02_test_failure CHECK(FALSE) NOT VALID");
        try{
            assertThrows(org.springframework.dao.DataAccessException.class,()->auth.login(command(installation,PASSWORD)));
            assertEquals("VIGENTE",state(first.sid()));assertEquals(1,count("sesion"));
            assertEquals(marker,jdbc.queryForObject("SELECT login_fallos_desde_utc FROM mycar.usuario",java.sql.Timestamp.class).toInstant());
            valid(first);
        }finally{jdbc.execute("ALTER TABLE mycar.sesion DROP CONSTRAINT rf02_test_failure");}
    }
    @Test void invalidInputsAndBearerAreSanitizedAndDoNotWrite()throws Exception{
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"idInstalacion\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/sesion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/sesion").header("Authorization","Bearer sensitive-invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value("Sesión no válida."));
        mvc.perform(post("/api/v1/auth/renovar").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0,count("sesion"));assertEquals(0,count("intento_login_fallido"));
    }
    @Test void restartOfApplicationServiceDoesNotInvalidateDatabaseSession()throws Exception{
        UUID installation=UUID.randomUUID();Tokens first=login(installation);
        var other=new SessionCredentials(System.getenv("MYCAR_RF02_TEST_SIGNING_KEY"),"PT5M","P1D");
        assertEquals(first.sid(),other.verify(first.access()).sessionId());valid(first);
    }
    @Test void noRf06OrInicioOrUnrelatedRouteBecomesPublic()throws Exception{
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inicio")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void temporalEmissionFailureRollsBackNewSessionReplacementAndFailureReset(boolean fractionalRemainder)throws Exception{
        UUID installation=UUID.randomUUID();
        Tokens previous=login(installation);
        denied(UUID.randomUUID());
        var accountBefore=jdbc.queryForMap("SELECT login_fallos_desde_utc,login_bloqueo_hasta_utc FROM mycar.usuario WHERE id_usuario=?",user);
        String refreshBefore=jdbc.queryForObject("SELECT refresh_token_hash FROM mycar.sesion WHERE id_sesion=?",String.class,previous.sid());
        int failuresBefore=count("intento_login_fallido");
        AuthenticationStore target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(store);
        var controlledStore=spy(target);
        var reads=new AtomicInteger();
        Instant started=previous.expires().minus(credentials.sessionDuration())
                .truncatedTo(ChronoUnit.SECONDS).plusSeconds(2).plusMillis(750);
        doAnswer(call->{
            int read=reads.incrementAndGet();
            if(read==3){
                // Observe actual uncommitted writes through the caller's JDBC transaction.
                assertEquals(2,count("sesion"));
                assertEquals("REVOCADA",state(previous.sid()));
                Instant deadline=started.plus(credentials.sessionDuration());
                return fractionalRemainder?deadline.truncatedTo(ChronoUnit.SECONDS):deadline;
            }
            return read==1?started.minusSeconds(1):started;
        }).when(controlledStore).now();
        var controlled=new SessionAuthentication(validator,controlledStore,credentials,passwords,transactions);
        assertEquals(AuthenticationFailure.Reason.INVALID_SESSION,
                assertThrows(AuthenticationFailure.class,()->controlled.login(command(installation,PASSWORD))).reason());

        assertEquals(3,reads.get());
        assertEquals(1,count("sesion"));
        assertEquals("VIGENTE",state(previous.sid()));
        assertNull(jdbc.queryForObject("SELECT revocada_utc FROM mycar.sesion WHERE id_sesion=?",java.sql.Timestamp.class,previous.sid()));
        assertEquals(refreshBefore,jdbc.queryForObject("SELECT refresh_token_hash FROM mycar.sesion WHERE id_sesion=?",String.class,previous.sid()));
        assertEquals(accountBefore,jdbc.queryForMap("SELECT login_fallos_desde_utc,login_bloqueo_hasta_utc FROM mycar.usuario WHERE id_usuario=?",user));
        assertEquals(failuresBefore,count("intento_login_fallido"));
        valid(previous);
    }
}
