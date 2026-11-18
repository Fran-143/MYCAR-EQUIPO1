package backend.autenticacion;

import backend.mfa.MfaService;
import backend.registro.RegistrationData;
import backend.registro.RegistrationException;
import backend.registro.RegistrationStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionAuthentication {
    public record Issued(long idSesion,String accessToken,Instant accessTokenExpiraUtc,
                         String refreshToken,Instant sesionExpiraUtc,String alcance){
        public Issued(long idSesion,String accessToken,Instant accessTokenExpiraUtc,
                      String refreshToken,Instant sesionExpiraUtc){
            this(idSesion,accessToken,accessTokenExpiraUtc,refreshToken,sesionExpiraUtc,"USUARIO");
        }
        @Override public String toString(){return "Issued[REDACTED]";}
    }
    public record Principal(long userId,long sessionId,UUID installation,Instant sessionExpiresAt,
                            String alcance,String role){
        public Principal(long userId,long sessionId,UUID installation,Instant sessionExpiresAt){
            this(userId,sessionId,installation,sessionExpiresAt,"USUARIO","USUARIO");
        }
        public boolean isAdmin(){return "ADMIN".equals(alcance) && "ADMIN".equals(role);}
        public boolean isUsuario(){return "USUARIO".equals(alcance);}
    }
    public sealed interface LoginResult permits LoginAuthenticated,LoginMfaChallenge {}
    public record LoginAuthenticated(Issued issued,String alcance) implements LoginResult {
        @Override public String toString(){return "LoginAuthenticated[REDACTED]";}
    }
    public record LoginMfaChallenge(MfaService.ChallengeSession challenge) implements LoginResult {
        @Override public String toString(){return "LoginMfaChallenge[REDACTED]";}
    }
    private record Outcome(LoginResult result,boolean rejected){
        @Override public String toString(){return "Outcome[REDACTED]";}
    }
    private final LoginValidator validator;
    private final AuthenticationStore store;
    private final SessionCredentials credentials;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;
    private final String dummyHash;
    private final boolean loginAutoAlta;
    private final RegistrationStore registrationStore;
    private final String privacyVersion;
    private final MfaService mfaService;

    public SessionAuthentication(LoginValidator validator,AuthenticationStore store,SessionCredentials credentials,
            @Qualifier("registrationPasswordEncoder") PasswordEncoder passwords,PlatformTransactionManager manager,
            RegistrationStore registrationStore,
            @Value("${mycar.auth.login-auto-alta:false}") boolean loginAutoAlta,
            @Value("${mycar.registro.privacidad.version-vigente:1.0}") String privacyVersion,
            MfaService mfaService){
        this.validator=validator;this.store=store;this.credentials=credentials;this.passwords=passwords;
        this.registrationStore=registrationStore;this.loginAutoAlta=loginAutoAlta;this.privacyVersion=privacyVersion;
        this.mfaService=mfaService;
        this.tx=new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }

    /** Compatibilidad RF02 usuario / tests: exige sesión completa (no desafío MFA). */
    public Issued login(LoginCommand input){
        LoginResult result=loginResult(input);
        if(result instanceof LoginAuthenticated auth)return auth.issued();
        throw AuthenticationFailure.unavailable();
    }

    public LoginResult loginResult(LoginCommand input){
        LoginCommand command=validator.validate(input);
        credentials.requireConfigured();
        Outcome outcome=tx.execute(status->{
            var account=store.lockAccount(command.correo()).orElse(null);
            if(account==null && loginAutoAlta){
                account=provisionLocalAccount(command);
            }
            if(account==null){passwords.matches(command.contrasena(),dummyHash);return new Outcome(null,true);}
            Instant checkedAt=store.now();
            if(account.blockedUntil()!=null && checkedAt.isBefore(account.blockedUntil()))return new Outcome(null,true);
            if(!passwords.matches(command.contrasena(),account.hash())){
                if("ACTIVA".equals(account.state()))store.recordFailure(account.id());
                return new Outcome(null,true);
            }
            if(!"ACTIVA".equals(account.state()))throw AuthenticationFailure.accountNotEnabled();
            store.resetFailures(account.id());
            if(account.isAdmin()){
                // MFA fuera de esta TX (REQUIRES_NEW en MfaService) tras commit de fallos/reset.
                return new Outcome(new LoginMfaChallenge(null),false); // placeholder; replaced after tx
            }
            Instant sessionStartedAt=store.now();
            store.replaceInstallation(account.id(),command.idInstalacion(),sessionStartedAt);
            String refresh=credentials.newRefresh();
            Instant expires=sessionStartedAt.plus(credentials.sessionDuration());
            long session=store.createSession(account.id(),command.idInstalacion(),SessionCredentials.hashRefresh(refresh),expires,sessionStartedAt,"USUARIO",null);
            Instant issuedAt=store.now();
            var access=credentials.issue(account.id(),session,command.idInstalacion(),issuedAt,expires);
            return new Outcome(new LoginAuthenticated(new Issued(session,access.value(),access.expiresAt(),refresh,expires,"USUARIO"),"USUARIO"),false);
        });
        if(outcome==null || outcome.rejected())throw AuthenticationFailure.credentials();
        if(outcome.result() instanceof LoginMfaChallenge){
            // Re-lock identity outside the password TX and open MFA challenge.
            var account=store.lockAccount(command.correo()).orElseThrow(AuthenticationFailure::credentials);
            if(!account.isAdmin())throw AuthenticationFailure.credentials();
            return new LoginMfaChallenge(mfaService.beginAdminLogin(account.id(),command.idInstalacion(),command.correo()));
        }
        return outcome.result();
    }

    /** Solo local/USB: alta mínima si el correo aún no existe (sin OTP). */
    private AuthenticationStore.Account provisionLocalAccount(LoginCommand command){
        try{
            registrationStore.createAccount(
                    new RegistrationData("Prueba","USB",command.correo(),LocalDate.of(1990,1,1),privacyVersion),
                    passwords.encode(command.contrasena()),
                    store.now());
        }catch(RegistrationException ex){
            if(ex.reason()!=RegistrationException.Reason.EMAIL_UNAVAILABLE)throw ex;
        }
        return store.lockAccount(command.correo()).orElse(null);
    }

    public Issued renew(UUID installation,String refresh){
        if(installation==null || refresh==null || !refresh.matches("[A-Za-z0-9_-]{43}"))
            throw AuthenticationFailure.invalid(Map.of("renovacion","Instalación y credencial de renovación obligatorias."));
        credentials.requireConfigured();
        String hash=SessionCredentials.hashRefresh(refresh);
        Issued issued=tx.execute(status->{
            var initial=store.findByRefreshHash(hash).orElse(null);
            if(initial==null)return null;
            if(store.lockAccount(initial.userId()).isEmpty())return null;
            var session=store.lockSession(initial.id()).orElse(null);
            Instant now=store.now();
            if(session==null || !session.installation().equals(installation) || !session.isValidAt(now)
                    || !MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),session.refreshHash().getBytes(StandardCharsets.US_ASCII)))return null;
            // Desafíos MFA no se renuevan: exigir login de nuevo.
            if("MFA_CHALLENGE".equals(session.alcance()) || "MFA_REENROLL".equals(session.alcance()))return null;
            String next=credentials.newRefresh();
            var access=credentials.issue(session.userId(),session.id(),installation,now,session.expiresAt());
            store.rotateRefresh(session.id(),SessionCredentials.hashRefresh(next),now);
            String alcance=session.alcance()==null?"USUARIO":session.alcance();
            return new Issued(session.id(),access.value(),access.expiresAt(),next,session.expiresAt(),alcance);
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
        // ADMIN: exigir rol vigente; si se revocó, invalidar.
        if("ADMIN".equals(session.alcance()) && !"ADMIN".equals(session.role()))throw AuthenticationFailure.session();
        String alcance=session.alcance()==null?"USUARIO":session.alcance();
        String role=session.role()==null?"USUARIO":session.role();
        return new Principal(session.userId(),session.id(),session.installation(),session.expiresAt(),alcance,role);
    }
}
