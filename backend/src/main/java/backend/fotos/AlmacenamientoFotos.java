package backend.fotos;

import backend.documentos.AlmacenamientoDocumentos;

/**
 * Puerto dedicado al bucket de fotos personalizadas (perfil/vehículo).
 * No reutiliza el bean de evidencias SOAT/TIVE/licencia.
 */
public final class AlmacenamientoFotos {
    private final AlmacenamientoDocumentos delegate;

    public AlmacenamientoFotos(AlmacenamientoDocumentos delegate) {
        this.delegate = delegate;
    }

    public void guardar(String objectKey, byte[] contenido, String contentType) {
        delegate.guardar(objectKey, contenido, contentType);
    }

    public byte[] leer(String objectKey) {
        return delegate.leer(objectKey);
    }

    public boolean confirmarExistencia(String objectKey) {
        return delegate.confirmarExistencia(objectKey);
    }

    public void borrarLogicoHuerfano(String objectKey) {
        delegate.borrarLogicoHuerfano(objectKey);
    }
}
