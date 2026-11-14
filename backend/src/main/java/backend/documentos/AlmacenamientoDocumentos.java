package backend.documentos;

import java.util.Map;

/** Puerto de almacenamiento privado (presign upload/download, existencia, huérfanos). */
public interface AlmacenamientoDocumentos {

    PresignUpload generarPresignUpload(String objectKey, String contentType, long maxBytes);

    PresignDownload generarPresignDownload(String objectKey);

    boolean confirmarExistencia(String objectKey);

    void borrarLogicoHuerfano(String objectKey);

    record PresignUpload(
            String url,
            Map<String, String> headers,
            java.time.Instant expiresAt,
            String objectKey
    ) { }

    record PresignDownload(
            String url,
            Map<String, String> headers,
            java.time.Instant expiresAt,
            String objectKey
    ) { }
}