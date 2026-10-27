package backend.autenticacion.web;

import backend.autenticacion.AuthenticationFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Map;

public final class AuthenticationErrors {
    public record ErrorBody(String codigo,String mensaje,Map<String,String> erroresPorCampo){ }
    private AuthenticationErrors(){ }
    public static ResponseEntity<ErrorBody> response(Exception ex){
        if(ex instanceof AuthenticationFailure failure){
            return switch(failure.reason()){
                case INVALID_INPUT -> body(400,"VALIDACION",failure.getMessage(),failure.fields());
                case INVALID_CREDENTIALS -> body(401,"CREDENCIALES_INVALIDAS",failure.getMessage(),Map.of());
                case INVALID_SESSION -> body(401,"SESION_INVALIDA",failure.getMessage(),Map.of());
                case ACCOUNT_NOT_ENABLED -> body(403,"CUENTA_NO_HABILITADA",failure.getMessage(),Map.of());
                case UNAVAILABLE -> body(503,"AUTENTICACION_NO_DISPONIBLE","Autenticación no disponible.",Map.of());
            };
        }
        if(ex instanceof DataAccessException)return body(503,"AUTENTICACION_NO_DISPONIBLE","Autenticación no disponible.",Map.of());
        return body(500,"ERROR_INTERNO","No se pudo completar la operación.",Map.of());
    }
    public static void write(HttpServletResponse response,ObjectMapper json,Exception ex)throws IOException{
        var result=response(ex);
        response.setStatus(result.getStatusCode().value());response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
        json.writeValue(response.getOutputStream(),result.getBody());
    }
    private static ResponseEntity<ErrorBody> body(int status,String code,String message,Map<String,String> fields){
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(new ErrorBody(code,message,fields));
    }
}
