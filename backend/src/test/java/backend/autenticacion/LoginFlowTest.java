package backend.autenticacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.nimbusds.jwt.SignedJWT;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginFlowTest {
    final LoginValidator validator=mock(LoginValidator.class);
    final AuthenticationStore store=mock(AuthenticationStore.class);
    final SessionCredentials credentials=mock(SessionCredentials.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final LoginCommand command=new LoginCommand("test@example.com","input-secret",UUID.randomUUID());
    final Instant now=Instant.now();
    SessionAuthentication auth;
    @BeforeEach void setup(){
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(passwords.encode(any())).thenReturn("dummy-test-hash");
        when(validator.validate(command)).thenReturn(command);when(store.now()).thenReturn(now);
        auth=new SessionAuthentication(validator,store,credentials,passwords,manager);
        clearInvocations(passwords);
    }
    @Test void failedPasswordIsCommittedBeforeAuthenticationIsRejected(){
        when(store.lockAccount(command.correo())).thenReturn(Optional.of(new AuthenticationStore.Account(1,"stored-hash","ACTIVA",null)));
        assertEquals(AuthenticationFailure.Reason.INVALID_CREDENTIALS,assertThrows(AuthenticationFailure.class,()->auth.login(command)).reason());
        var order=inOrder(store,manager);order.verify(store).recordFailure(1);order.verify(manager).commit(any());
        verify(manager,never()).rollback(any());verify(store,never()).createSession(anyLong(),any(),anyString(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"ACTIVA","SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void activeRestrictionNeverChecksPasswordOrIncrementsOrResetsFailures(String state){
        when(store.lockAccount(command.correo())).thenReturn(Optional.of(new AuthenticationStore.Account(1,"stored-hash",state,now.plusSeconds(60))));
        assertThrows(AuthenticationFailure.class,()->auth.login(command));
        verifyNoInteractions(passwords);verify(store,never()).recordFailure(anyLong());verify(store,never()).resetFailures(anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void nonActiveWrongPasswordIsGenericWithoutNewFailures(String state){
        when(store.lockAccount(command.correo())).thenReturn(Optional.of(new AuthenticationStore.Account(1,"stored-hash",state,null)));
        assertEquals(AuthenticationFailure.Reason.INVALID_CREDENTIALS,assertThrows(AuthenticationFailure.class,()->auth.login(command)).reason());
        verify(passwords).matches(command.contrasena(),"stored-hash");
        verify(store,never()).recordFailure(anyLong());verify(store,never()).resetFailures(anyLong());
        verify(store,never()).replaceInstallation(anyLong(),any(),any());
        verify(store,never()).createSession(anyLong(),any(),anyString(),any(),any());
        verify(credentials,never()).newRefresh();verify(credentials,never()).issue(anyLong(),anyLong(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void nonActiveCorrectPasswordIsDeniedBeforeSuccessOperations(String state){
        when(store.lockAccount(command.correo())).thenReturn(Optional.of(new AuthenticationStore.Account(1,"stored-hash",state,null)));
        when(passwords.matches(command.contrasena(),"stored-hash")).thenReturn(true);
        assertEquals(AuthenticationFailure.Reason.ACCOUNT_NOT_ENABLED,assertThrows(AuthenticationFailure.class,()->auth.login(command)).reason());
        verify(passwords).matches(command.contrasena(),"stored-hash");
        verify(store,never()).recordFailure(anyLong());verify(store,never()).resetFailures(anyLong());
        verify(store,never()).replaceInstallation(anyLong(),any(),any());
        verify(store,never()).createSession(anyLong(),any(),anyString(),any(),any());
        verify(credentials,never()).newRefresh();verify(credentials,never()).issue(anyLong(),anyLong(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"SUSPENDIDA","PENDIENTE_ELIMINACION"})
    void expiredRestrictionVerifiesPasswordWithoutClearingHistory(String state){
        when(store.lockAccount(command.correo())).thenReturn(Optional.of(new AuthenticationStore.Account(1,"stored-hash",state,now.minusSeconds(1))));
        assertEquals(AuthenticationFailure.Reason.INVALID_CREDENTIALS,assertThrows(AuthenticationFailure.class,()->auth.login(command)).reason());
        verify(passwords).matches(command.contrasena(),"stored-hash");
        verify(store,never()).recordFailure(anyLong());verify(store,never()).resetFailures(anyLong());
        verify(store,never()).createSession(anyLong(),any(),anyString(),any(),any());
    }
    @Test void malformedRenewalCannotQueryOrRotateSessions(){
        assertThrows(AuthenticationFailure.class,()->auth.renew(null,"not-a-token"));
        verifyNoInteractions(store);
    }

    @ParameterizedTest @ValueSource(longs={0,2,600})
    void loginUsesPostPasswordSessionTimeAndPostInsertIssueTime(long passwordSeconds)throws Exception{
        Instant checked=Instant.parse("2030-01-01T12:00:00.900Z");
        Instant started=checked.plusSeconds(passwordSeconds);
        Instant issued=started.plusMillis(200);
        Instant deadline=started.plusSeconds(10);
        var clock=new AtomicReference<>(checked);
        when(store.now()).thenAnswer(call->clock.get());
        when(store.lockAccount(command.correo())).thenReturn(Optional.of(
                new AuthenticationStore.Account(1,"stored-hash","ACTIVA",checked.minusSeconds(1))));
        when(passwords.matches(command.contrasena(),"stored-hash")).thenAnswer(call->{clock.set(started);return true;});
        String refresh="r".repeat(43);
        var realCredentials=spy(new SessionCredentials(SessionCredentialsTest.randomKey(),"PT1S","PT10S"));
        doReturn(refresh).when(realCredentials).newRefresh();
        when(store.createSession(1,command.idInstalacion(),SessionCredentials.hashRefresh(refresh),deadline,started))
                .thenAnswer(call->{clock.set(issued);return 12L;});
        var service=new SessionAuthentication(validator,store,realCredentials,passwords,manager);

        var result=service.login(command);
        var jwt=SignedJWT.parse(result.accessToken()).getJWTClaimsSet();
        assertEquals(deadline,result.sesionExpiraUtc());
        assertEquals(Instant.parse("2030-01-01T12:00:00Z").plusSeconds(passwordSeconds+1),jwt.getIssueTime().toInstant());
        assertEquals(issued.plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),result.accessTokenExpiraUtc());
        assertEquals(result.accessTokenExpiraUtc(),jwt.getExpirationTime().toInstant());
        assertTrue(issued.isBefore(result.accessTokenExpiraUtc()));
        var order=inOrder(store,passwords,realCredentials,manager);
        order.verify(store).now();
        order.verify(passwords).matches(command.contrasena(),"stored-hash");
        order.verify(store).resetFailures(1);
        order.verify(store).now();
        order.verify(store).replaceInstallation(1,command.idInstalacion(),started);
        order.verify(store).createSession(1,command.idInstalacion(),SessionCredentials.hashRefresh(refresh),deadline,started);
        order.verify(store).now();
        order.verify(realCredentials).issue(1,12,command.idInstalacion(),issued,deadline);
        order.verify(manager).commit(any());
        verify(manager,never()).rollback(any());
    }

    @Test void renewalReadsTimeAfterBothLocksAndKeepsAbsoluteDeadline(){
        UUID installation=command.idInstalacion();
        String refresh="r".repeat(43),hash=SessionCredentials.hashRefresh(refresh);
        Instant beforeLocks=Instant.parse("2030-01-01T00:00:00Z"),afterLocks=beforeLocks.plusSeconds(600);
        Instant deadline=afterLocks.plusSeconds(10);
        var clock=new AtomicReference<>(beforeLocks);
        var session=new AuthenticationStore.Session(12,1,installation,hash,"VIGENTE",deadline,null,"ACTIVA");
        when(store.now()).thenAnswer(call->clock.get());
        when(store.findByRefreshHash(hash)).thenReturn(Optional.of(session));
        when(store.lockAccount(1)).thenReturn(Optional.of(new AuthenticationStore.Account(1,"hash","ACTIVA",null)));
        when(store.lockSession(12)).thenAnswer(call->{clock.set(afterLocks);return Optional.of(session);});
        when(credentials.newRefresh()).thenReturn("n".repeat(43));
        when(credentials.issue(1,12,installation,afterLocks,deadline))
                .thenReturn(new SessionCredentials.Access("access-test",deadline));
        var result=auth.renew(installation,refresh);
        assertEquals(deadline,result.sesionExpiraUtc());
        var order=inOrder(store,credentials,manager);
        order.verify(store).lockAccount(1);
        order.verify(store).lockSession(12);
        order.verify(store).now();
        order.verify(credentials).issue(1,12,installation,afterLocks,deadline);
        order.verify(store).rotateRefresh(12,SessionCredentials.hashRefresh("n".repeat(43)),afterLocks);
        order.verify(manager).commit(any());
    }
}
