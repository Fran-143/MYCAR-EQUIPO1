package backend.autenticacion;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class SessionCredentials {
    private static final String ISSUER="mycar-backend";
    private static final String AUDIENCE="mycar-session";
    private final String encodedSecret,accessDuration,sessionDuration;
    private final SecureRandom random=new SecureRandom();
    private volatile Settings settings;
    private record Settings(NimbusJwtEncoder encoder,NimbusJwtDecoder decoder,Duration access,Duration session){
        @Override public String toString(){return "Settings[REDACTED]";}
    }
    public record Access(String value,Instant expiresAt){
        @Override public String toString(){return "Access[REDACTED]";}
    }
    public record Claims(long userId,long sessionId,UUID installation,Instant expiresAt){ }

    public SessionCredentials(@Value("${mycar.auth.jwt-secret-base64:}") String encodedSecret,
            @Value("${mycar.auth.access-ttl:}") String accessDuration,
            @Value("${mycar.auth.session-ttl:}") String sessionDuration){
        this.encodedSecret=encodedSecret;this.accessDuration=accessDuration;this.sessionDuration=sessionDuration;
    }
    public void requireConfigured(){settings();}
    public Duration sessionDuration(){return settings().session();}
    private synchronized Settings settings(){
        if(settings!=null)return settings;
        try{
            byte[] key=Base64.getDecoder().decode(encodedSecret);
            Duration access=Duration.parse(accessDuration),session=Duration.parse(sessionDuration);
            if(key.length<32 || access.getSeconds()<1 || session.getSeconds()<1 || access.getNano()!=0
                    || session.getNano()!=0 || access.compareTo(session)>0)throw new IllegalArgumentException();
            var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(key));
            var decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key,"HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(Duration.ZERO),new JwtIssuerValidator(ISSUER)));
            settings=new Settings(encoder,decoder,access,session);
            return settings;
        }catch(RuntimeException ex){throw AuthenticationFailure.unavailable();}
    }
    public String newRefresh(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public static String hashRefresh(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 unavailable");}
    }
    public Access issue(long userId,long sessionId,UUID installation,Instant now,Instant sessionExpiry){
        Settings configured=settings();
        Instant accessExpiry=now.plus(configured.access());
        if(accessExpiry.isAfter(sessionExpiry))accessExpiry=sessionExpiry;
        accessExpiry=accessExpiry.truncatedTo(ChronoUnit.SECONDS);
        if(!now.isBefore(accessExpiry))throw AuthenticationFailure.session();
        var claims=JwtClaimsSet.builder().issuer(ISSUER).audience(List.of(AUDIENCE))
                .subject(Long.toString(userId)).id(UUID.randomUUID().toString())
                .issuedAt(now.truncatedTo(ChronoUnit.SECONDS)).expiresAt(accessExpiry)
                .claim("sid",Long.toString(sessionId)).claim("installation",installation.toString()).build();
        try{
            String jwt=configured.encoder().encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
            return new Access(jwt,accessExpiry);
        }catch(JwtException ex){throw AuthenticationFailure.unavailable();}
    }
    public Claims verify(String token){
        Settings configured=settings();
        try{
            Jwt jwt=configured.decoder().decode(token);
            long user=Long.parseLong(jwt.getSubject()),session=Long.parseLong(jwt.getClaimAsString("sid"));
            if(user<=0 || session<=0 || jwt.getIssuedAt()==null || jwt.getExpiresAt()==null
                    || !jwt.getAudience().contains(AUDIENCE) || !jwt.getIssuedAt().isBefore(jwt.getExpiresAt()))throw new IllegalArgumentException();
            return new Claims(user,session,UUID.fromString(jwt.getClaimAsString("installation")),jwt.getExpiresAt());
        }catch(RuntimeException ex){throw AuthenticationFailure.session();}
    }
}
