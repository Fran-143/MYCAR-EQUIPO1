package backend.documentos;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Almacenamiento en memoria para pruebas: confirmar tras simular subida. */
public class FakeAlmacenamientoDocumentos implements AlmacenamientoDocumentos {
    private final Set<String> objetos = ConcurrentHashMap.newKeySet();
    private final Set<String> huerfanos = ConcurrentHashMap.newKeySet();

    @Override
    public PresignUpload generarPresignUpload(String objectKey, String contentType, long maxBytes) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        return new PresignUpload(
                "fake://upload/" + objectKey,
                Map.of("Content-Type", contentType == null ? "application/octet-stream" : contentType),
                Instant.now().plusSeconds(600),
                objectKey);
    }

    @Override
    public PresignDownload generarPresignDownload(String objectKey) {
        if (!objetos.contains(objectKey)) {
            throw new ErrorAlmacenamiento("OBJETO_NO_ENCONTRADO", "El documento no está disponible.");
        }
        return new PresignDownload(
                "fake://download/" + objectKey,
                Map.of(),
                Instant.now().plusSeconds(300),
                objectKey);
    }

    @Override
    public boolean confirmarExistencia(String objectKey) {
        return objetos.contains(objectKey);
    }

    @Override
    public void borrarLogicoHuerfano(String objectKey) {
        objetos.remove(objectKey);
        huerfanos.add(objectKey);
    }

    /** Simula que el cliente completó la subida presignada. */
    public void simularSubida(String objectKey) {
        RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
        objetos.add(objectKey);
        huerfanos.remove(objectKey);
    }

    public Set<String> objetosPresentes() {
        return Set.copyOf(objetos);
    }
}