package backend.cierresesion;

import backend.autenticacion.AuthenticationFailure;
import backend.autenticacion.AuthenticationStore;
import backend.autenticacion.SessionCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** RF06 — TDD de CloseSession (Arrange / Act / Assert). */
class CloseSessionTest {
    final SessionCredentials credentials=mock(SessionCredentials.class);
    final AuthenticationStore sessions=mock(AuthenticationStore.class);
    final SessionRevocationStore revocations=mock(SessionRevocationStore.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final UUID installation=UUID.randomUUID();
    final Instant now=Instant.now();
    CloseSession close;
    @BeforeEach void setup(){
        // Arrange compartido: sesión vigente autenticada
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(credentials.verify("test-proof")).thenReturn(new SessionCredentials.Claims(10,20,installation,now.plusSeconds(60)));
        when(sessions.lockAccount(10L)).thenReturn(Optional.of(new AuthenticationStore.Account(10,"unused","ACTIVA",null)));
        when(sessions.lockSession(20)).thenReturn(Optional.of(session("VIGENTE",null)));
        when(sessions.now()).thenReturn(now);
        when(revocations.revoke(20,10,installation,now)).thenReturn(1);
        close=new CloseSession(credentials,sessions,revocations,manager);
    }
    AuthenticationStore.Session session(String state,Instant revoked){return new AuthenticationStore.Session(20,10,installation,"unused",state,now.plusSeconds(1000),revoked,"ACTIVA");}

    @Test void signedIdentityDeterminesOnlyTargetAndCommitFollowsUpdate(){
        // Act
        close.close("test-proof");

        // Assert
        var order=inOrder(credentials,sessions,revocations,manager);
        order.verify(credentials).verify("test-proof");order.verify(sessions).lockAccount(10L);
        order.verify(sessions).lockSession(20);order.verify(sessions).now();
        order.verify(revocations).revoke(20,10,installation,now);order.verify(manager).commit(any());
        verifyNoMoreInteractions(revocations);verify(sessions,never()).replaceInstallation(anyLong(),any(),any());
        verify(sessions,never()).resetFailures(anyLong());
    }
    @Test void authenticatedDuplicateDoesNotRewriteTimestamp(){
        // Arrange
        when(sessions.lockSession(20)).thenReturn(Optional.of(session("REVOCADA",now.minusSeconds(1))));

        // Act
        close.close("test-proof");

        // Assert
        verifyNoInteractions(revocations);verify(manager).commit(any());
    }
    @Test void invalidProofCannotStartDatabaseTransaction(){
        // Arrange
        when(credentials.verify("test-proof")).thenThrow(AuthenticationFailure.session());

        // Act + Assert
        assertThrows(AuthenticationFailure.class,()->close.close("test-proof"));
        verifyNoInteractions(sessions,revocations,manager);
    }
    @Test void expiryIsRecheckedAfterWaitingForLocks(){
        // Arrange
        when(sessions.now()).thenReturn(now.plusSeconds(61));

        // Act + Assert
        assertThrows(AuthenticationFailure.class,()->close.close("test-proof"));
        verifyNoInteractions(revocations);verify(manager).rollback(any());
    }
    @ParameterizedTest @ValueSource(strings={"EXPIRADA","REVOCADA"})
    void invalidStateCannotBeReportedAsConfirmed(String state){
        // Arrange
        when(sessions.lockSession(20)).thenReturn(Optional.of(session(state,null)));

        // Act + Assert
        assertThrows(AuthenticationFailure.class,()->close.close("test-proof"));verifyNoInteractions(revocations);
    }
    @Test void updateThatAffectedNoRowIsNotSuccess(){
        // Arrange
        when(revocations.revoke(20,10,installation,now)).thenReturn(0);

        // Act + Assert
        assertThrows(AuthenticationFailure.class,()->close.close("test-proof"));verify(manager).rollback(any());
    }
    @Test void databaseFailureRollsBackInsteadOfConfirming(){
        // Arrange
        when(revocations.revoke(20,10,installation,now)).thenThrow(new DataAccessResourceFailureException("fixture failure"));

        // Act + Assert
        assertThrows(DataAccessResourceFailureException.class,()->close.close("test-proof"));
        verify(manager).rollback(any());verify(manager,never()).commit(any());
    }
}
