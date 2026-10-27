package backend.autenticacion.web;

import backend.autenticacion.AuthenticationFailure;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes=AuthenticationController.class)
public class AuthenticationErrorHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthenticationErrors.ErrorBody> malformed(){
        return AuthenticationErrors.response(AuthenticationFailure.invalid(Map.of("solicitud","JSON o UUID inválido.")));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthenticationErrors.ErrorBody> handle(Exception ex){return AuthenticationErrors.response(ex);}
}
