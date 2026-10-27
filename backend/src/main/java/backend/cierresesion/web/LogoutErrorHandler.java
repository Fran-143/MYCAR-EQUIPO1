package backend.cierresesion.web;

import backend.autenticacion.web.AuthenticationErrors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=LogoutController.class)
public class LogoutErrorHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthenticationErrors.ErrorBody> handle(Exception ex){return AuthenticationErrors.response(ex);}
}
