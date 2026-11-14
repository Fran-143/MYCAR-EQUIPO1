package backend.documentos;

/** Falla explícita cuando no hay adaptador de storage activo. */
public final class AlmacenamientoNoConfigurado implements AlmacenamientoDocumentos {

    @Override
    public PresignUpload generarPresignUpload(String objectKey, String contentType, long maxBytes) {
        throw new ErrorAlmacenamiento("STORAGE_NO_CONFIGURADO",
                "El almacenamiento de documentos no está configurado.");
    }

    @Override
    public PresignDownload generarPresignDownload(String objectKey) {
        throw new ErrorAlmacenamiento("STORAGE_NO_CONFIGURADO",
                "El almacenamiento de documentos no está configurado.");
    }

    @Override
    public boolean confirmarExistencia(String objectKey) {
        throw new ErrorAlmacenamiento("STORAGE_NO_CONFIGURADO",
                "El almacenamiento de documentos no está configurado.");
    }

    @Override
    public void borrarLogicoHuerfano(String objectKey) {
        throw new ErrorAlmacenamiento("STORAGE_NO_CONFIGURADO",
                "El almacenamiento de documentos no está configurado.");
    }
}