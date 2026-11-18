package backend.mfa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MfaProperties {
    private final String issuer;
    private final int maxIntentos;
    private final Duration bloqueo;
    private final Duration desafioTtl;
    private final Duration adminSessionTtl;
    private final int recoveryCount;

    public MfaProperties(
            @Value("${mycar.mfa.issuer:MyCar}") String issuer,
            @Value("${mycar.mfa.max-intentos:5}") int maxIntentos,
            @Value("${mycar.mfa.bloqueo:PT10M}") String bloqueo,
            @Value("${mycar.mfa.desafio-ttl:PT5M}") String desafioTtl,
            @Value("${mycar.mfa.admin-session-ttl:PT15M}") String adminSessionTtl,
            @Value("${mycar.mfa.recovery-count:8}") int recoveryCount){
        this.issuer=issuer==null||issuer.isBlank()?"MyCar":issuer.trim();
        this.maxIntentos=Math.max(1,maxIntentos);
        this.bloqueo=Duration.parse(bloqueo);
        this.desafioTtl=Duration.parse(desafioTtl);
        this.adminSessionTtl=Duration.parse(adminSessionTtl);
        this.recoveryCount=Math.max(1,recoveryCount);
    }
    public String issuer(){return issuer;}
    public int maxIntentos(){return maxIntentos;}
    public Duration bloqueo(){return bloqueo;}
    public Duration desafioTtl(){return desafioTtl;}
    public Duration adminSessionTtl(){return adminSessionTtl;}
    public int recoveryCount(){return recoveryCount;}
}
