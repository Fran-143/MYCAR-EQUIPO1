package backend.cierresesion;

import backend.autenticacion.AuthenticationFailure;
import backend.autenticacion.LoginCommand;
import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.SessionCredentials;
import backend.cierresesion.web.LogoutController;
import backend.cierresesion.web.LogoutErrorHandler;
import backend.registro.RegisterAccount;
import backend.registro.RegistrationCommand;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="MYCAR_RF06_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf06_test_[a-z0-9_]+")
@SpringBootTest(properties={"spring.config.import=optional:classpath:rf06-no-local-env.properties",
        "mycar.registro.privacidad.version-vigente=FIXTURE-NO-LEGAL",
        "logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ExtendWith(OutputCaptureExtension.class)
class LogoutIntegrationTest {
    static final String EMAIL="rf06@example.com",PASSWORD="FixturePass1!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        String url=System.getenv("MYCAR_RF06_TEST_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mycar_rf06_test_[a-z0-9_]+"))throw new IllegalStateException("Disposable RF06 database required");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->System.getenv("MYCAR_RF06_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("MYCAR_RF06_TEST_PASSWORD"));
        p.add("mycar.auth.jwt-secret-base64",()->System.getenv("MYCAR_RF06_TEST_SIGNING_KEY"));
        p.add("mycar.auth.access-ttl",()->"PT5M");p.add("mycar.auth.session-ttl",()->"P1D");
    }
    @Autowired RegisterAccount registration;
    @Autowired SessionAuthentication auth;
    @Autowired SessionCredentials credentials;
    @Autowired CloseSession close;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    long user;
    @BeforeEach void fixtures(){
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).startsWith("mycar_rf06_test_"));
        for(String t:new String[]{"sesion","intento_login_fallido","preferencia_notificacion","config_recordatorio_km_usuario","suscripcion","usuario"})jdbc.update("DELETE FROM mycar."+t);
        user=register(EMAIL);
    }
    long register(String email){return registration.register(new RegistrationCommand("Elsa","Pérez",email,LocalDate.of(1995,1,1),PASSWORD,PASSWORD,true,"FIXTURE-NO-LEGAL"));}
    SessionAuthentication.Issued login(UUID installation){return auth.login(new LoginCommand(EMAIL,PASSWORD,installation));}
    String state(long id){return jdbc.queryForObject("SELECT estado_sesion FROM mycar.sesion WHERE id_sesion=?",String.class,id);}
    Instant revoked(long id){var t=jdbc.queryForObject("SELECT revocada_utc FROM mycar.sesion WHERE id_sesion=?",java.sql.Timestamp.class,id);return t==null?null:t.toInstant();}
    void logout(String token)throws Exception{mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token))
            .andExpect(status().isNoContent()).andExpect(content().string(""))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().doesNotExist("Set-Cookie"));}
    void valid(SessionAuthentication.Issued issued)throws Exception{mvc.perform(get("/api/v1/auth/sesion").header("Authorization","Bearer "+issued.accessToken())).andExpect(status().isOk());}

    @Test void successRevokesOnlyCurrentSessionAndRejectsItsAccessAndRefresh(CapturedOutput output)throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);var other=login(UUID.randomUUID());
        Instant before=Instant.now();logout(first.accessToken());
        assertEquals("REVOCADA",state(first.idSesion()));assertNotNull(revoked(first.idSesion()));
        assertFalse(revoked(first.idSesion()).isBefore(before));assertEquals("VIGENTE",state(other.idSesion()));
        mvc.perform(get("/api/v1/auth/sesion").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
        assertThrows(AuthenticationFailure.class,()->auth.renew(installation,first.refreshToken()));valid(other);
        assertFalse(output.getAll().contains(first.accessToken()));assertFalse(output.getAll().contains(first.refreshToken()));assertFalse(output.getAll().contains(PASSWORD));
    }
    @Test void duplicateAuthorizedLogoutPreservesTimestamp()throws Exception{
        var first=login(UUID.randomUUID());logout(first.accessToken());Instant date=revoked(first.idSesion());
        logout(first.accessToken());assertEquals(date,revoked(first.idSesion()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.sesion",Integer.class));
    }
    @Test void newLoginIsAllowedAfterSuccessfulLogout()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);logout(first.accessToken());
        var next=login(installation);assertNotEquals(first.idSesion(),next.idSesion());valid(next);
    }
    @Test void delayedLogoutForOldSessionNeverRevokesNewLogin()throws Exception{
        UUID installation=UUID.randomUUID();var old=login(installation);
        // Old remote logout not yet sent/confirmed. RF02 creates a new valid session anyway.
        var next=login(installation);logout(old.accessToken());
        assertEquals("REVOCADA",state(old.idSesion()));assertEquals("VIGENTE",state(next.idSesion()));valid(next);
    }
    @Test void unrelatedRequestsAndUnsupportedMethodDoNotCloseSession()throws Exception{
        var first=login(UUID.randomUUID());valid(first);
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isForbidden());
        assertEquals("VIGENTE",state(first.idSesion()));assertNull(revoked(first.idSesion()));valid(first);
        // Backend boundary for A01/A02: absent an authorized POST, nothing revokes. No Android UI is simulated.
    }
    @Test void missingInvalidAndRefreshProofNeverAuthorize()throws Exception{
        var first=login(UUID.randomUUID());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        for(String token:new String[]{"invalid-sensitive-proof",first.refreshToken()})
            mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value("Sesión no válida."));
        assertEquals("VIGENTE",state(first.idSesion()));valid(first);
    }
    @Test void expiredAccessDoesNotRenewOrConfirmRemoteLogout()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);
        var expired=credentials.issue(user,first.idSesion(),installation,Instant.now().minusSeconds(600),first.sesionExpiraUtc());
        String hash=jdbc.queryForObject("SELECT refresh_token_hash FROM mycar.sesion",String.class);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+expired.value())).andExpect(status().isUnauthorized());
        assertEquals("VIGENTE",state(first.idSesion()));assertNull(revoked(first.idSesion()));
        assertEquals(hash,jdbc.queryForObject("SELECT refresh_token_hash FROM mycar.sesion",String.class));
        // This proves expired access can remain renewable; Android must not confuse it with EX03.
        assertNotNull(auth.renew(installation,first.refreshToken()));
    }
    @Test void definitivelyExpiredSessionCannotBeConfirmedAsNewRevocation()throws Exception{
        var first=login(UUID.randomUUID());jdbc.update("UPDATE mycar.sesion SET estado_sesion='EXPIRADA',expira_utc=clock_timestamp()-interval '1 second'");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
        assertEquals("EXPIRADA",state(first.idSesion()));assertNull(revoked(first.idSesion()));
        // EX03 itself is local and sends no HTTP request; this is an additional server safety check.
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void nonActiveAccountCannotAuthorizeRemoteLogout(String accountState)throws Exception{
        var first=login(UUID.randomUUID());jdbc.update("UPDATE mycar.usuario SET estado_cuenta=?",accountState);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
        assertEquals("VIGENTE",state(first.idSesion()));
    }
    @Test void jwtOwnerInstallationAndActualRowMustAgree()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);var other=login(UUID.randomUUID());
        long foreignUser=register("foreign@example.com");
        var foreign=auth.login(new LoginCommand("foreign@example.com",PASSWORD,UUID.randomUUID()));
        var wrongInstallation=credentials.issue(user,first.idSesion(),UUID.randomUUID(),Instant.now(),first.sesionExpiraUtc());
        var wrongOwner=credentials.issue(user,foreign.idSesion(),installation,Instant.now(),first.sesionExpiraUtc());
        var missing=credentials.issue(user,Long.MAX_VALUE,installation,Instant.now(),first.sesionExpiraUtc());
        assertNotEquals(user,foreignUser);
        for(var proof:new SessionCredentials.Access[]{wrongInstallation,wrongOwner,missing})
            mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+proof.value())).andExpect(status().isUnauthorized());
        valid(first);valid(other);valid(foreign);
    }
    @Test void unsignedIdentityInBodyCannotChooseOtherSession()throws Exception{
        var first=login(UUID.randomUUID());var other=login(UUID.randomUUID());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken())
                .contentType("application/json").content("{\"idSesion\":"+other.idSesion()+"}"))
                .andExpect(status().isNoContent());
        assertEquals("REVOCADA",state(first.idSesion()));assertEquals("VIGENTE",state(other.idSesion()));valid(other);
    }
    @Test void jwtSignedWithForeignKeyIsRejected()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);byte[] key=new byte[32];new SecureRandom().nextBytes(key);
        var foreign=new SessionCredentials(Base64.getEncoder().encodeToString(key),"PT5M","P1D");
        var forged=foreign.issue(user,first.idSesion(),installation,Instant.now(),first.sesionExpiraUtc());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+forged.value())).andExpect(status().isUnauthorized());valid(first);
    }
    @Test void databaseErrorReturns503RollsBackAndDoesNotPreventLaterLogin()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);
        jdbc.execute("ALTER TABLE mycar.sesion ADD CONSTRAINT rf06_test_failure CHECK(estado_sesion<>'REVOCADA') NOT VALID");
        try{
            String response=mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken()))
                    .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains(first.accessToken()));assertFalse(response.contains("rf06_test_failure"));
            assertEquals("VIGENTE",state(first.idSesion()));assertNull(revoked(first.idSesion()));valid(first);
        }finally{jdbc.execute("ALTER TABLE mycar.sesion DROP CONSTRAINT rf06_test_failure");}
        valid(login(installation));
    }
    @Test void responseLostAfterCommitDoesNotUndoRevocationAndNewLoginWorks()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);
        Filter dropResponse=(request,response,chain)->{chain.doFilter(request,response);throw new IOException("Simulated loss after commit");};
        var lost=MockMvcBuilders.standaloneSetup(new LogoutController(close)).setControllerAdvice(new LogoutErrorHandler()).addFilters(dropResponse).build();
        assertThrows(Exception.class,()->lost.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken())));
        assertEquals("REVOCADA",state(first.idSesion()));valid(login(installation));
        // Server state is known to this test. A client without a response cannot assert remote confirmation.
    }
    @Test void repeatedConcurrentLogoutIsIdempotent()throws Exception{
        var first=login(UUID.randomUUID());var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Boolean> attempt=()->{barrier.await(10,TimeUnit.SECONDS);close.close(first.accessToken());return true;};
            var a=pool.submit(attempt);var b=pool.submit(attempt);assertTrue(a.get(30,TimeUnit.SECONDS));assertTrue(b.get(30,TimeUnit.SECONDS));
        }
        Instant date=revoked(first.idSesion());logout(first.accessToken());assertEquals(date,revoked(first.idSesion()));
    }
    @Test void concurrentLogoutAndNewLoginNeverCloseTheNewSession()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);close.close(first.accessToken());return true;});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return login(installation);});
            assertTrue(a.get(30,TimeUnit.SECONDS));var next=b.get(30,TimeUnit.SECONDS);valid(next);assertEquals("REVOCADA",state(first.idSesion()));
        }
    }
    @Test void concurrentRefreshCannotReopenARevokedSession()throws Exception{
        UUID installation=UUID.randomUUID();var first=login(installation);var barrier=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);close.close(first.accessToken());return true;});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return auth.renew(installation,first.refreshToken());}catch(AuthenticationFailure ex){return null;}});
            assertTrue(a.get(30,TimeUnit.SECONDS));var renewed=b.get(30,TimeUnit.SECONDS);
            assertEquals("REVOCADA",state(first.idSesion()));
            if(renewed!=null)assertThrows(AuthenticationFailure.class,()->auth.renew(installation,renewed.refreshToken()));
        }
    }
    @Test void logoutDoesNotDeleteAccountPreferencesOrChangeFailedLoginHistory()throws Exception{
        var first=login(UUID.randomUUID());
        assertThrows(AuthenticationFailure.class,()->auth.login(new LoginCommand(EMAIL,"wrong",UUID.randomUUID())));
        String accountBefore=jdbc.queryForMap("SELECT * FROM mycar.usuario").toString();
        logout(first.accessToken());assertTrue(accountBefore.equals(jdbc.queryForMap("SELECT * FROM mycar.usuario").toString()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.intento_login_fallido",Integer.class));
        for(String table:new String[]{"suscripcion","preferencia_notificacion","config_recordatorio_km_usuario"})
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar."+table,Integer.class));
    }
}
