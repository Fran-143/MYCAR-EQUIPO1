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

    /** Solo tokens del usuario autenticado — prueba controlada, no broadcast. */
    public record ProbarResponse(boolean ok, int tokens, int enviados, int invalidos, int errores) {
    }
}
