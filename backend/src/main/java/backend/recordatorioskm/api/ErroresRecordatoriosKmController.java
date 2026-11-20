package backend.recordatorioskm.api;

import backend.recordatorioskm.api.dto.ContratoRecordatoriosKm;
import backend.recordatorioskm.model.ErrorRecordatorioKm;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RecordatoriosKmController.class)
public class ErroresRecordatoriosKmController {

    @ExceptionHandler(ErrorRecordatorioKm.class)
    ResponseEntity<ContratoRecordatoriosKm.ErrorResponse> error(ErrorRecordatorioKm ex) {
        HttpStatus status = switch (ex.codigo()) {
            case "VEHICULO_NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "VEHICULO_INACTIVO", "FRECUENCIA_INVALIDA", "SIN_LECTURA_VALIDA", "DATOS_INVALIDOS"
                    -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(new ContratoRecordatoriosKm.ErrorResponse(ex.codigo(), ex.getMessage()));
    }
}
