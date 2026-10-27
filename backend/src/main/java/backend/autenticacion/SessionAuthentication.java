package backend.autenticacion;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionAuthentication {
    public record Issued(long idSesion,String accessToken,Instant accessTokenExpiraUtc,
                         String refreshToken,Instant sesionExpiraUtc){
        @Override public String toString(){return "Issued[REDACTED]";}
    }
    public record Principal(long userId,long sessionId,UUID installation,Instant sessionExpiresAt){ }
    private record Outcome(Issued issued,boolean rejected){
        @Override public String toString(){return "Outcome[REDACTED]";}
    }
    private final LoginValidator validator;
    private final AuthenticationStore store;
    private final SessionCredentials credentials;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;
    private final String dummyHash;

    public SessionAuthentication(LoginValidator validator,AuthenticationStore store,SessionCredentials credentials,
            @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords,PlatformTransactionManager manager){
        this.validator=validator;this.store=store;this.credentials=credentials;this.passwords=passwords;
        this.tx=new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }

    public Issued login(LoginCommand input){
        LoginCommand command=validator.validate(input);
        credentials.requireConfigured();
        Outcome outcome=tx.execute(status->{
            var account=store.lockAccount(command.correo()).orElse(null);
            if(account==null){passwords.matches(command.contrasena(),dummyHash);return new Outcome(null,true);}
            Instant checkedAt=store.now();
            if(account.blockedUntil()!=null && checkedAt.isBefore(account.blockedUntil()))return new Outcome(null,true);
            if(!passwords.matches(command.contrasena(),account.hash())){
                // EC-RF02-01: only ACTIVA accounts accumulate new failures, as in V4.
                if("ACTIVA".equals(account.state()))store.recordFailure(account.id());
                return new Outcome(null,true); // Commit the failure BEFORE rejecting outside the transaction.
            }
            if(!"ACTIVA".equals(account.state()))throw AuthenticationFailure.accountNotEnabled();
            store.resetFailures(account.id());
            // Password verification must not consume the new session's lifetime.
            Instant sessionStartedAt=store.now();
            store.replaceInstallation(account.id(),command.idInstalacion(),sessionStartedAt);
            String refresh=credentials.newRefresh();
            Instant expires=sessionStartedAt.plus(credentials.sessionDuration());
            long session=store.createSession(account.id(),command.idInstalacion(),SessionCredentials.hashRefresh(refresh),expires,sessionStartedAt);
            // Re-read after persistence waits; issue rejects an exhausted absolute deadline.
            Instant issuedAt=store.now();
            var access=credentials.issue(account.id(),session,command.idInstalacion(),issuedAt,expires);
            return new Outcome(new Issued(session,access.value(),access.expiresAt(),refresh,expires),false);
        });
        if(outcome==null || outcome.rejected())throw AuthenticationFailure.credentials();
        return outcome.issued();
    }

    public Issued renew(UUID installation,String refresh){
        if(installation==null || refresh==null || !refresh.matches("[A-Za-z0-9_-]{43}"))
            throw AuthenticationFailure.invalid(Map.of("renovacion","Instalación y credencial de renovación obligatorias."));
        credentials.requireConfigured();
        String hash=SessionCredentials.hashRefresh(refresh);
        Issued issued=tx.execute(status->{
            var initial=store.findByRefreshHash(hash).orElse(null);
            if(initial==null)return null;
            // All commands use user -> session lock order, including concurrent login/refresh.
            if(store.lockAccount(initial.userId()).isEmpty())return null;
            var session=store.lockSession(initial.id()).orElse(null);
            Instant now=store.now();
            if(session==null || !session.installation().equals(installation) || !session.isValidAt(now)
                    || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),session.refreshHash().getBytes(StandardCharsets.US_ASCII)))return null;
            String next=credentials.newRefresh();
            var access=credentials.issue(session.userId(),session.id(),installation,now,session.expiresAt());
            store.rotateRefresh(session.id(),SessionCredentials.hashRefresh(next),now);
            return new Issued(session.id(),access.value(),access.expiresAt(),next,session.expiresAt());
        });
        if(issued==null)throw AuthenticationFailure.session();
        return issued;
    }

    public Principal validateAccess(String token){
        var claims=credentials.verify(token);
        var session=store.findSession(claims.sessionId()).orElseThrow(AuthenticationFailure::session);
        Instant now=store.now();
        if(!session.isValidAt(now) || session.userId()!=claims.userId()
                || !session.installation().equals(claims.installation())
                || !now.isBefore(claims.expiresAt()) || claims.expiresAt().isAfter(session.expiresAt()))throw AuthenticationFailure.session();
        return new Principal(session.userId(),session.id(),session.installation(),session.expiresAt());
    }
}
