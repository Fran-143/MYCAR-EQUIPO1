package backend.push.api.dto;

import java.util.UUID;

public final class ContratoPush {
    private ContratoPush() {
    }

    public record RegistrarTokenRequest(UUID idInstalacion, String proveedor, String token) {
    }

    public record DesvincularTokenRequest(UUID idInstalacion) {
    }

    public record OkResponse(boolean ok) {
        public static OkResponse si() {
            return new OkResponse(true);
        }
    }

    /**
     * Opcional: token FCM vivo del dispositivo que pulsó "Probar".
     * Si viene, se hace upsert antes del envío (evita tokens=0 tras fallos de red).
     */
    public record ProbarRequest(UUID idInstalacion, String token) {
    }

    /** Solo tokens del usuario autenticado — prueba controlada, no broadcast. */
    public record ProbarResponse(boolean ok, int tokens, int enviados, int invalidos, int errores) {
    }

    /**
     * Prueba RF43: crea aviso en bandeja (KILOMETRAJE) y envía FCM a tokens del usuario.
     * No usa outbox ni ventana horaria (verificación inmediata).
     */
    public record ProbarKmResponse(
            boolean ok,
            boolean bandejaCreada,
            Long idNotificacion,
            Long idVehiculo,
            int tokens,
            int enviados,
            int invalidos,
            int errores) {
    }
}
