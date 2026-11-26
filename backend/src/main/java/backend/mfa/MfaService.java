package backend.mfa;

import backend.autenticacion.AuthenticationStore;
import backend.autenticacion.SessionAuthentication;
import backend.autenticacion.SessionCredentials;
import backend.mfa.persistence.JdbcMfaStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MfaService {
    public record ChallengeSession(UUID desafioId,String estado,long idSesion,String accessToken,
                                   Instant accessTokenExpiraUtc,String refreshToken,Instant sesionExpiraUtc,
                                   String alcance){
        @Override public String toString(){return "ChallengeSession[REDACTED]";}
    }
    public record EnrollStart(String otpauthUri,String secretoManual){
        @Override public String toString(){return "EnrollStart[REDACTED]";}
    }
    public record RecoveryCodes(List<String> codigos,UUID loteId){
        @Override public String toString(){return "RecoveryCodes[count="+codigos.size()+"]";}
    }

    private final JdbcMfaStore mfa;
    private final AuthenticationStore auth;
    private final SessionCredentials credentials;
    private final MfaCrypto crypto;
    private final TotpService totp;
    private final MfaProperties props;
    private final TransactionTemplate tx;
    private final SecureRandom random=new SecureRandom();

    public MfaService(JdbcMfaStore mfa,AuthenticationStore auth,SessionCredentials credentials,
                      MfaCrypto crypto,TotpService totp,MfaProperties props,
                      PlatformTransactionManager manager){
        this.mfa=mfa;this.auth=auth;this.credentials=credentials;this.crypto=crypto;this.totp=totp;this.props=props;
        this.tx=new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ChallengeSession beginAdminLogin(long userId,UUID installation,String correo){
        crypto.exigir();
        credentials.requireConfigured();
        return tx.execute(status->{
            var account=auth.lockAccount(userId).orElseThrow(MfaFailure::forbidden);
            if(!"ADMIN".equals(account.role()) || !"ACTIVA".equals(account.state()))throw MfaFailure.forbidden();
            Instant now=auth.now();
            var config=mfa.findConfig(userId).orElse(null);
            String tipo;
            String estado;
            if(config==null || "PENDIENTE".equals(config.estado())){
                tipo="ENROLL";estado="MFA_ENROLL_REQUERIDO";
            }else if("ACTIVA".equals(config.estado())){
                tipo="LOGIN";estado="MFA_REQUERIDO";
            }else throw MfaFailure.forbidden();
            auth.replaceInstallation(userId,installation,now);
            return issueChallenge(userId,installation,tipo,estado,now);
        });
    }

    public EnrollStart iniciarEnroll(SessionAuthentication.Principal principal){
        crypto.exigir();
        return tx.execute(status->{
            var session=requireAlcance(principal,"MFA_CHALLENGE","MFA_REENROLL");
            Instant now=auth.now();
            String role=mfa.roleOf(session.userId());
            if(!"ADMIN".equals(role))throw MfaFailure.forbidden();
            String secret=totp.newSecretBase32();
            String sealed=crypto.cifrarSecreto(session.userId(),secret);
            if("MFA_REENROLL".equals(session.alcance())){
                mfa.resetParaReenroll(session.userId(),sealed,crypto.keyId(),now);
            }else{
                mfa.upsertPendiente(session.userId(),sealed,crypto.keyId(),now);
            }
            mfa.auditar(session.userId(),null,"ENROLL_INICIADO",null);
            String accountLabel="admin-"+session.userId();
            return new EnrollStart(totp.otpauthUri(props.issuer(),accountLabel,secret),secret);
        });
    }

    public RecoveryCodes confirmarEnroll(SessionAuthentication.Principal principal,String codigo){
        crypto.exigir();
        return tx.execute(status->{
            var session=requireAlcance(principal,"MFA_CHALLENGE","MFA_REENROLL");
            Instant now=auth.now();
            var config=mfa.lockConfig(session.userId()).orElseThrow(MfaFailure::forbidden);
            if(!"PENDIENTE".equals(config.estado()))throw MfaFailure.forbidden();
            String secret=crypto.descifrarSecreto(session.userId(),config.secretoCifrado());
            var step=totp.verify(secret,codigo==null?null:codigo.trim(),now);
            if(!step.present()){
                mfa.auditar(session.userId(),null,"ENROLL_CODIGO_INVALIDO",null);
                throw MfaFailure.invalidCode(1,props.maxIntentos());
            }
            mfa.activar(session.userId(),step.step(),now);
            mfa.invalidarRecovery(session.userId());
            UUID lote=UUID.randomUUID();
            List<String> plain=new ArrayList<>(props.recoveryCount());
            List<String> hashes=new ArrayList<>(props.recoveryCount());
            for(int i=0;i<props.recoveryCount();i++){
                String code=newRecoveryCode();
                plain.add(code);
                hashes.add(hash(code));
            }
            mfa.insertRecoveryHashes(session.userId(),lote,hashes,now);
            mfa.auditar(session.userId(),null,"ENROLL_CONFIRMADO",null);
            return new RecoveryCodes(List.copyOf(plain),lote);
        });
    }

    public SessionAuthentication.Issued confirmarRespaldo(SessionAuthentication.Principal principal){
        crypto.exigir();
        return tx.execute(status->{
            var session=requireAlcance(principal,"MFA_CHALLENGE","MFA_REENROLL");
            Instant now=auth.now();
            var config=mfa.findConfig(session.userId()).orElseThrow(MfaFailure::forbidden);
            if(!"ACTIVA".equals(config.estado()))throw MfaFailure.forbidden();
            auth.revokeSession(session.id(),now);
            mfa.auditar(session.userId(),null,"ENROLL_RESPALDO_CONFIRMADO",null);
            return issueAdminSession(session.userId(),session.installation(),now);
        });
    }

    public SessionAuthentication.Issued verificar(SessionAuthentication.Principal principal,UUID desafioId,String codigo){
        crypto.exigir();
        return tx.execute(status->{
            var session=requireAlcance(principal,"MFA_CHALLENGE");
            Instant now=auth.now();
            var desafio=requireOpenChallenge(desafioId,session,now,"LOGIN");
            assertNotBlocked(desafio,now);
            var config=mfa.lockConfig(session.userId()).orElseThrow(MfaFailure::forbidden);
            if(!"ACTIVA".equals(config.estado()))throw MfaFailure.forbidden();
            String secret=crypto.descifrarSecreto(session.userId(),config.secretoCifrado());
            var step=totp.verify(secret,codigo==null?null:codigo.trim(),now);
            if(!step.present()){
                failAttempt(desafio,now);
                throw MfaFailure.invalidCode(desafio.intentos()+1,props.maxIntentos());
            }
            if(!mfa.aceptarPasoAtomico(session.userId(),step.step())){
                mfa.auditar(session.userId(),desafioId,"LOGIN_PASO_REUSADO",null);
                throw MfaFailure.stepReused();
            }
            mfa.consumirDesafio(desafioId,now);
            auth.revokeSession(session.id(),now);
            mfa.auditar(session.userId(),desafioId,"LOGIN_TOTP_OK",null);
            return issueAdminSession(session.userId(),session.installation(),now);
        });
    }

    public SessionAuthentication.Issued recuperar(SessionAuthentication.Principal principal,UUID desafioId,String codigoRespaldo){
        crypto.exigir();
        return tx.execute(status->{
            var session=requireAlcance(principal,"MFA_CHALLENGE");
            Instant now=auth.now();
            var desafio=requireOpenChallenge(desafioId,session,now,"LOGIN");
            assertNotBlocked(desafio,now);
            String normalized=normalizeRecovery(codigoRespaldo);
            if(normalized==null)throw MfaFailure.invalid(Map.of("codigoRespaldo","Código de recuperación inválido."));
            var consumed=mfa.consumirRecovery(session.userId(),hash(normalized),now);
            if(consumed.isEmpty()){
                failAttempt(desafio,now);
                throw MfaFailure.invalidCode(desafio.intentos()+1,props.maxIntentos());
            }
            mfa.consumirDesafio(desafioId,now);
            auth.revokeSession(session.id(),now);
            mfa.auditar(session.userId(),desafioId,"RECOVERY_OK",null);
            return issueReenrollSession(session.userId(),session.installation(),now);
        });
    }

    private ChallengeSession issueChallenge(long userId,UUID installation,String tipo,String estado,Instant now){
        UUID desafioId=UUID.randomUUID();
        String refresh=credentials.newRefresh();
        Instant expires=now.plus(props.desafioTtl());
        long sid=auth.createSession(userId,installation,SessionCredentials.hashRefresh(refresh),expires,now,"MFA_CHALLENGE",null);
        // token_hash del desafío = hash del access JWT no; usamos hash del refresh+desafioId como nonce opaco
        String desafioToken=credentials.newRefresh();
        mfa.crearDesafio(desafioId,userId,tipo,SessionCredentials.hashRefresh(desafioToken),installation,expires,now);
        Instant issuedAt=auth.now();
        var access=credentials.issue(userId,sid,installation,issuedAt,expires);
        mfa.auditar(userId,desafioId,"DESAFIO_"+tipo,null);
        return new ChallengeSession(desafioId,estado,sid,access.value(),access.expiresAt(),refresh,expires,"MFA_CHALLENGE");
    }

    private SessionAuthentication.Issued issueAdminSession(long userId,UUID installation,Instant now){
        String refresh=credentials.newRefresh();
        Instant expires=now.plus(props.adminSessionTtl());
        auth.replaceInstallation(userId,installation,now);
        long sid=auth.createSession(userId,installation,SessionCredentials.hashRefresh(refresh),expires,now,"ADMIN",now);
        Instant issuedAt=auth.now();
        var access=credentials.issue(userId,sid,installation,issuedAt,expires);
        return new SessionAuthentication.Issued(sid,access.value(),access.expiresAt(),refresh,expires,"ADMIN");
    }

    private SessionAuthentication.Issued issueReenrollSession(long userId,UUID installation,Instant now){
        String refresh=credentials.newRefresh();
        Instant expires=now.plus(props.desafioTtl());
        auth.replaceInstallation(userId,installation,now);
        long sid=auth.createSession(userId,installation,SessionCredentials.hashRefresh(refresh),expires,now,"MFA_REENROLL",null);
        Instant issuedAt=auth.now();
        var access=credentials.issue(userId,sid,installation,issuedAt,expires);
        return new SessionAuthentication.Issued(sid,access.value(),access.expiresAt(),refresh,expires,"MFA_REENROLL");
    }

    private AuthenticationStore.Session requireAlcance(SessionAuthentication.Principal principal,String... allowed){
        var session=auth.findSession(principal.sessionId()).orElseThrow(MfaFailure::forbidden);
        Instant now=auth.now();
        if(!session.isValidAt(now) || session.userId()!=principal.userId())throw MfaFailure.forbidden();
        for(String a:allowed){
            if(a.equals(session.alcance()))return session;
        }
        throw MfaFailure.forbidden();
    }

    private JdbcMfaStore.Desafio requireOpenChallenge(UUID desafioId,AuthenticationStore.Session session,Instant now,String tipo){
        if(desafioId==null)throw MfaFailure.invalid(Map.of("desafioId","Desafío obligatorio."));
        var desafio=mfa.lockDesafio(desafioId).orElseThrow(MfaFailure::challengeExpired);
        if(desafio.userId()!=session.userId() || !desafio.installation().equals(session.installation())
                || !tipo.equals(desafio.tipo()) || desafio.consumidoUtc()!=null)throw MfaFailure.forbidden();
        if(!now.isBefore(desafio.expiraUtc()))throw MfaFailure.challengeExpired();
        return desafio;
    }

    private void assertNotBlocked(JdbcMfaStore.Desafio desafio,Instant now){
        if(desafio.bloqueoHasta()!=null && now.isBefore(desafio.bloqueoHasta()))
            throw MfaFailure.rateLimited(desafio.bloqueoHasta());
    }

    private void failAttempt(JdbcMfaStore.Desafio desafio,Instant now){
        int next=desafio.intentos()+1;
        Instant bloqueo=null;
        if(next>=props.maxIntentos())bloqueo=now.plus(props.bloqueo());
        mfa.registrarIntentoFallido(desafio.id(),next,bloqueo);
        mfa.auditar(desafio.userId(),desafio.id(),"INTENTO_FALLIDO","n="+next);
        if(bloqueo!=null)throw MfaFailure.rateLimited(bloqueo);
    }

    private String newRecoveryCode(){
        byte[] bytes=new byte[5];
        random.nextBytes(bytes);
        String hex=HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
        return hex.substring(0,4)+"-"+hex.substring(4,8)+"-"+hex.substring(8);
    }
    private static String normalizeRecovery(String raw){
        if(raw==null)return null;
        String n=raw.trim().toUpperCase(Locale.ROOT).replace(" ","").replace("-","");
        if(!n.matches("[A-F0-9]{10}"))return null;
        return n.substring(0,4)+"-"+n.substring(4,8)+"-"+n.substring(8);
    }
    private static String hash(String value){
        try{
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
