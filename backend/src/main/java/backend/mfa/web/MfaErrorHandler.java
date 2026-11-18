package backend.mfa.web;

import backend.mfa.MfaFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(basePackageClasses=MfaController.class)
public class MfaErrorHandler {
    @ExceptionHandler(MfaFailure.class)
    public ResponseEntity<Map<String,Object>> handle(MfaFailure ex){
        Map<String,Object> body=new LinkedHashMap<>();
        String codigo=switch(ex.reason()){
            case INVALID_INPUT -> "VALIDACION";
            case INVALID_CODE -> "MFA_CODIGO_INVALIDO";
            case CHALLENGE_EXPIRED -> "MFA_DESAFIO_EXPIRADO";
            case RATE_LIMITED -> "MFA_BLOQUEO";
            case FORBIDDEN -> "MFA_PROHIBIDO";
            case UNAVAILABLE -> "MFA_NO_DISPONIBLE";
            case STEP_REUSED -> "MFA_PASO_REUSADO";
        };
        int status=switch(ex.reason()){
            case INVALID_INPUT -> 400;
            case INVALID_CODE, STEP_REUSED -> 401;
            case CHALLENGE_EXPIRED -> 401;
            case RATE_LIMITED -> 429;
            case FORBIDDEN -> 403;
            case UNAVAILABLE -> 503;
        };
        body.put("codigo",codigo);
        body.put("mensaje",ex.getMessage());
        body.put("erroresPorCampo",ex.fields());
        if(ex.intento()!=null)body.put("intento",ex.intento());
        if(ex.maxIntentos()!=null)body.put("maxIntentos",ex.maxIntentos());
        if(ex.bloqueoHasta()!=null)body.put("bloqueoHastaUtc",ex.bloqueoHasta());
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(body);
    }
}
