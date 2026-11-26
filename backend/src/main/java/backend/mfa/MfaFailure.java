package backend.mfa;

import java.time.Instant;
import java.util.Map;

public final class MfaFailure extends RuntimeException {
    public enum Reason {
        INVALID_INPUT, INVALID_CODE, CHALLENGE_EXPIRED, RATE_LIMITED, FORBIDDEN, UNAVAILABLE, STEP_REUSED
    }
    private final Reason reason;
    private final Map<String,String> fields;
    private final Instant bloqueoHasta;
    private final Integer intento;
    private final Integer maxIntentos;

    private MfaFailure(Reason reason,String message,Map<String,String> fields,
                       Instant bloqueoHasta,Integer intento,Integer maxIntentos){
        super(message);
        this.reason=reason;this.fields=Map.copyOf(fields);
        this.bloqueoHasta=bloqueoHasta;this.intento=intento;this.maxIntentos=maxIntentos;
    }
    public static MfaFailure invalid(Map<String,String> fields){
        return new MfaFailure(Reason.INVALID_INPUT,"Revisa los datos ingresados.",fields,null,null,null);
    }
    public static MfaFailure invalidCode(int intento,int max){
        return new MfaFailure(Reason.INVALID_CODE,"Código incorrecto.",Map.of(),null,intento,max);
    }
    public static MfaFailure challengeExpired(){
        return new MfaFailure(Reason.CHALLENGE_EXPIRED,"El desafío MFA expiró. Inicia sesión de nuevo.",Map.of(),null,null,null);
    }
    public static MfaFailure rateLimited(Instant until){
        return new MfaFailure(Reason.RATE_LIMITED,"Demasiados intentos. Espera antes de reintentar.",Map.of(),until,null,null);
    }
    public static MfaFailure forbidden(){
        return new MfaFailure(Reason.FORBIDDEN,"Operación no permitida.",Map.of(),null,null,null);
    }
    public static MfaFailure unavailable(){
        return new MfaFailure(Reason.UNAVAILABLE,"MFA no disponible.",Map.of(),null,null,null);
    }
    public static MfaFailure stepReused(){
        return new MfaFailure(Reason.STEP_REUSED,"Código ya utilizado. Espera el siguiente.",Map.of(),null,null,null);
    }
    public Reason reason(){return reason;}
    public Map<String,String> fields(){return fields;}
    public Instant bloqueoHasta(){return bloqueoHasta;}
    public Integer intento(){return intento;}
    public Integer maxIntentos(){return maxIntentos;}
}
