package backend.registro.verificacion.web;

import backend.registro.RegistrationCommand;
import backend.registro.RegistrationException;
import backend.registro.web.RegistrationRequest;
import backend.registro.verificacion.DevMailGateway;
import backend.registro.verificacion.MailModo;
import backend.registro.verificacion.VerificationDispatcher;
import backend.registro.verificacion.VerificationOriginService;
import backend.registro.verificacion.VerificationService;
import backend.registro.verificacion.VerificationService.Failure;
import backend.registro.verificacion.VerificationService.Operation;
import backend.registro.verificacion.VerificationService.Origin;
import backend.registro.verificacion.VerificationService.Result;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/registro/verificacion")
public class VerificationController {
    private final VerificationService verification;
    private final VerificationDispatcher dispatcher;
    private final VerificationOriginService origins;
    private final ObjectProvider<DevMailGateway> devMail;
    private final boolean desarrollo;
    private final SecureRandom random = new SecureRandom();

    public VerificationController(VerificationService verification, VerificationDispatcher dispatcher,
            VerificationOriginService origins, ObjectProvider<DevMailGateway> devMail,
            @Value("${mycar.verificacion.mail.modo:desarrollo}") String modo) {
        this.verification = verification;
        this.dispatcher = dispatcher;
        this.origins = origins;
        this.devMail = devMail;
        this.desarrollo = !MailModo.ocultaCodigoDesarrollo(modo);
    }

    @PostMapping(path = "/origen", produces = "application/json")
    public OriginResponse origen() {
        Origin o = origins.create();
        return new OriginResponse(o.id().toString(), o.secret());
    }

    @PostMapping(path = "/emitir", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> emitir(@RequestBody EmitRequest body) {
        Origin origin = new Origin(parseUuid(body.idOrigen(), "idOrigen"), require(body.secretoOrigen(), "secretoOrigen"));
        UUID operationId = body.idOperacion() == null || body.idOperacion().isBlank()
                ? UUID.randomUUID() : parseUuid(body.idOperacion(), "idOperacion");
        String operationSecret = body.secretoOperacion() == null || body.secretoOperacion().isBlank()
                ? newSecret() : body.secretoOperacion();
        Operation operation = new Operation(operationId, operationSecret);
        UUID request = body.idSolicitud() == null || body.idSolicitud().isBlank()
                ? UUID.randomUUID() : parseUuid(body.idSolicitud(), "idSolicitud");
        long revision = body.revision() == null ? 0L : body.revision();
        RegistrationCommand form = body.formulario().toCommand();
        Result result = verification.emit(origin, operation, request, revision, form);
        if (!"EMISION_CREADA".equals(result.code())) {
            return ResponseEntity.status(statusFor(result.code(), 429))
                    .body(new ErrorBody(result.code(), mensaje(result.code()),
                            result.blockUntil() == null ? null : result.blockUntil().toString()));
        }
        if (result.emission() != null) {
            dispatcher.dispatch(result.emission());
        }
        String codigo = null;
        if (desarrollo && result.emission() != null) {
            DevMailGateway gateway = devMail.getIfAvailable();
            if (gateway != null) {
                codigo = gateway.takeCodeForEmission(result.emission());
            }
        }
        EmitResponse response = new EmitResponse(
                result.code(),
                result.operation() == null ? operationId.toString() : result.operation().toString(),
                operationSecret,
                result.emission() == null ? null : result.emission().toString(),
                result.revision(),
                result.blockUntil() == null ? null : result.blockUntil().toString(),
                codigo);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(path = "/completar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> completar(@RequestBody CompleteRequest body) {
        Operation operation = new Operation(
                parseUuid(body.idOperacion(), "idOperacion"),
                require(body.secretoOperacion(), "secretoOperacion"));
        UUID request = body.idSolicitud() == null || body.idSolicitud().isBlank()
                ? UUID.randomUUID() : parseUuid(body.idSolicitud(), "idSolicitud");
        long revision = body.revision() == null ? 0L : body.revision();
        UUID emission = parseUuid(body.idEmision(), "idEmision");
        Result result = verification.complete(operation, request, revision, emission,
                body.codigo(), body.formulario().toCommand());
        if ("CUENTA_CREADA".equals(result.code())) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("resultado", "CUENTA_CREADA", "destino", "INICIO_SESION"));
        }
        return ResponseEntity.status(statusFor(result.code(), 422))
                .body(new ErrorBody(result.code(), mensaje(result.code()),
                        result.blockUntil() == null ? null : result.blockUntil().toString()));
    }

    @PostMapping(path = "/reenviar", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> reenviar(@RequestBody EmitRequest body) {
        return emitir(body);
    }

    private static int statusFor(String code, int defaultOk) {
        return switch (code) {
            case "EMISION_CREADA" -> 202;
            case "CUENTA_CREADA" -> 201;
            case "CODIGO_INCORRECTO" -> 422;
            case "BLOQUEO_INICIADO", "BLOQUEO_ACTIVO", "LIMITE_TECNICO", "LIMITE_ENVIO",
                 "ESPERA_REENVIO", "RESERVA_PENDIENTE", "PAUSA_TECNICA" -> 429;
            case "CORREO_NO_DISPONIBLE" -> 409;
            default -> code != null && (code.startsWith("CODIGO_") || code.contains("INVALID")
                    || code.contains("EXPIR")) ? 422 : defaultOk;
        };
    }

    private static String mensaje(String code) {
        return switch (code == null ? "" : code) {
            case "CODIGO_INCORRECTO" -> "Código incorrecto.";
            case "BLOQUEO_INICIADO", "BLOQUEO_ACTIVO" ->
                    "Demasiados intentos fallidos. Espera aproximadamente 1 hora antes de reintentar.";
            case "ESPERA_REENVIO" ->
                    "Ya enviamos un código a este correo. Espera 10 minutos antes de pedir otro.";
            case "LIMITE_ENVIO", "LIMITE_TECNICO" ->
                    "Alcanzaste el límite de envíos. Espera un rato e inténtalo de nuevo.";
            case "RESERVA_PENDIENTE", "PAUSA_TECNICA" ->
                    "Hay un envío en curso. Espera unos segundos e inténtalo de nuevo.";
            case "CODIGO_VENCIDO", "CODIGO_CANCELADO", "CODIGO_CONSUMIDO" ->
                    "El código ya no es válido. Solicita uno nuevo.";
            default -> "No se pudo completar la verificación.";
        };
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(require(raw, field));
        } catch (IllegalArgumentException ex) {
            throw new Failure("SOLICITUD_INVALIDA");
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new Failure("SOLICITUD_INVALIDA");
        return value;
    }

    public record OriginResponse(String idOrigen, String secretoOrigen) {}
    public record EmitRequest(String idOrigen, String secretoOrigen, String idOperacion, String secretoOperacion,
            String idSolicitud, Long revision, RegistrationRequest formulario) {}
    public record EmitResponse(String resultado, String idOperacion, String secretoOperacion, String idEmision,
            long revision, String bloqueoHastaUtc, String codigoDesarrollo) {}
    public record CompleteRequest(String idOperacion, String secretoOperacion, String idSolicitud, Long revision,
            String idEmision, String codigo, RegistrationRequest formulario) {}
    public record ErrorBody(String codigo, String mensaje, String bloqueoHastaUtc) {}
}
