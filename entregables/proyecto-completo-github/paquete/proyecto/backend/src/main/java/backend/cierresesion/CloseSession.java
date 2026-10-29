package backend.cierresesion;

import backend.autenticacion.AuthenticationFailure;
import backend.autenticacion.AuthenticationStore;
import backend.autenticacion.SessionCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class CloseSession {
    private final SessionCredentials credentials;
    private final AuthenticationStore sessions;
    private final SessionRevocationStore revocations;
    private final TransactionTemplate tx;

    public CloseSession(SessionCredentials credentials,AuthenticationStore sessions,
            SessionRevocationStore revocations,PlatformTransactionManager manager){
        this.credentials=credentials;this.sessions=sessions;this.revocations=revocations;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void close(String accessToken){
        // Reuse RF02 signature/issuer/audience/expiry verification. No JWT implementation is duplicated.
        var proof=credentials.verify(accessToken);
        tx.executeWithoutResult(status->{
            // Same lock order as login/renewal. Never choose a session from a free client ID.
            var account=sessions.lockAccount(proof.userId()).orElseThrow(AuthenticationFailure::session);
            var session=sessions.lockSession(proof.sessionId()).orElseThrow(AuthenticationFailure::session);
            Instant now=sessions.now();
            if(!"ACTIVA".equals(account.state()) || session.userId()!=proof.userId()
                    || !session.installation().equals(proof.installation())
                    || !now.isBefore(proof.expiresAt()) || !now.isBefore(session.expiresAt())
                    || proof.expiresAt().isAfter(session.expiresAt()))throw AuthenticationFailure.session();
            // An authenticated duplicate confirms only this already revoked session; its date is immutable here.
            if("REVOCADA".equals(session.state()) && session.revokedAt()!=null)return;
            if(!"VIGENTE".equals(session.state()) || session.revokedAt()!=null)throw AuthenticationFailure.session();
            if(revocations.revoke(session.id(),session.userId(),session.installation(),now)!=1)
                throw AuthenticationFailure.session();
        });
        // The API may confirm success only after this transaction has committed.
    }
}
