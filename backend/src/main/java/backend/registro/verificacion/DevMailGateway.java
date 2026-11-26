package backend.registro.verificacion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adaptador de desarrollo: acepta el envío y retiene el código para la respuesta API. */
public final class DevMailGateway implements MailGateway {
    private final Map<String, String> codesByIdempotency = new ConcurrentHashMap<>();
    private final Map<String, String> codesByEmissionKey = new ConcurrentHashMap<>();

    @Override
    public Outcome send(Message message) {
        if (message == null || message.code() == null || message.idempotencyKey() == null) {
            return new Rejected(true);
        }
        codesByIdempotency.put(message.idempotencyKey(), message.code());
        // clave_idempotencia_proveedor = "rf01/"+emission
        codesByEmissionKey.put(message.idempotencyKey(), message.code());
        return new Accepted("dev-" + Integer.toHexString(message.idempotencyKey().hashCode()));
    }

    public String codeForProviderKey(String providerKey) {
        return providerKey == null ? null : codesByIdempotency.get(providerKey);
    }

    public String takeCodeForEmission(java.util.UUID emission) {
        if (emission == null) return null;
        return codesByIdempotency.get("rf01/" + emission);
    }
}
