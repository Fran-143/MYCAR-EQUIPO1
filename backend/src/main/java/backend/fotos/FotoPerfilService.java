package backend.fotos;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class FotoPerfilService {
    private final JdbcTemplate jdbc;
    private final AlmacenamientoFotos almacenamiento;

    public FotoPerfilService(JdbcTemplate jdbc, AlmacenamientoFotos almacenamiento) {
        this.jdbc = jdbc;
        this.almacenamiento = almacenamiento;
    }

    public record MetaFoto(String contentType, long tamanoBytes) { }

    @Transactional
    public void registrar(long idUsuario, byte[] bytes, String contentTypeDeclarado) {
        exigirCuentaActiva(idUsuario);
        MetaRef actual = leerMeta(idUsuario);
        if (actual != null && actual.objectKey() != null && !actual.objectKey().isBlank()) {
            throw FotoException.conflict("Ya tienes una foto de perfil. Usa cambiar foto.");
        }
        var validada = ValidadorFotoPersonalizada.validarYLimpiar(bytes, contentTypeDeclarado);
        String nuevaClave = RutasFotoPersonalizada.clavePerfil(idUsuario, validada.extension());
        RutasFotoPersonalizada.exigirClaveValida(nuevaClave);
        try {
            almacenamiento.guardar(nuevaClave, validada.bytesSinExif(), validada.contentType());
        } catch (RuntimeException ex) {
            throw FotoException.storage("No se pudo guardar la foto. Intenta de nuevo.");
        }
        int updated = jdbc.update("""
                UPDATE mycar.usuario
                   SET foto_storage_object_key = ?,
                       foto_content_type = ?,
                       foto_tamano_bytes = ?,
                       foto_actualizada_en = NOW()
                 WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                   AND (foto_storage_object_key IS NULL OR foto_storage_object_key = '')
                """, nuevaClave, validada.contentType(), (long) validada.bytesSinExif().length, idUsuario);
        if (updated != 1) {
            borrarSeguro(nuevaClave);
            throw FotoException.conflict("Ya tienes una foto de perfil. Usa cambiar foto.");
        }
    }

    @Transactional
    public void actualizar(long idUsuario, byte[] bytes, String contentTypeDeclarado) {
        exigirCuentaActiva(idUsuario);
        MetaRef actual = leerMeta(idUsuario);
        if (actual == null || actual.objectKey() == null || actual.objectKey().isBlank()) {
            throw FotoException.notFound("No hay foto de perfil para actualizar.");
        }
        String anterior = actual.objectKey();
        var validada = ValidadorFotoPersonalizada.validarYLimpiar(bytes, contentTypeDeclarado);
        String nuevaClave = RutasFotoPersonalizada.clavePerfil(idUsuario, validada.extension());
        RutasFotoPersonalizada.exigirClaveValida(nuevaClave);
        try {
            almacenamiento.guardar(nuevaClave, validada.bytesSinExif(), validada.contentType());
        } catch (RuntimeException ex) {
            throw FotoException.storage("No se pudo guardar la foto. Intenta de nuevo.");
        }
        int updated = jdbc.update("""
                UPDATE mycar.usuario
                   SET foto_storage_object_key = ?,
                       foto_content_type = ?,
                       foto_tamano_bytes = ?,
                       foto_actualizada_en = NOW()
                 WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                   AND foto_storage_object_key = ?
                """, nuevaClave, validada.contentType(), (long) validada.bytesSinExif().length,
                idUsuario, anterior);
        if (updated != 1) {
            borrarSeguro(nuevaClave);
            throw FotoException.conflict("La foto cambió en otra sesión. Vuelve a intentarlo.");
        }
        borrarSeguro(anterior);
    }

    @Transactional
    public void eliminar(long idUsuario) {
        exigirCuentaActiva(idUsuario);
        MetaRef actual = leerMeta(idUsuario);
        if (actual == null || actual.objectKey() == null || actual.objectKey().isBlank()) {
            throw FotoException.notFound("No hay foto de perfil para eliminar.");
        }
        String anterior = actual.objectKey();
        int updated = jdbc.update("""
                UPDATE mycar.usuario
                   SET foto_storage_object_key = NULL,
                       foto_content_type = NULL,
                       foto_tamano_bytes = NULL,
                       foto_actualizada_en = NULL
                 WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                   AND foto_storage_object_key = ?
                """, idUsuario, anterior);
        if (updated != 1) {
            throw FotoException.conflict("La foto cambió en otra sesión. Vuelve a intentarlo.");
        }
        borrarSeguro(anterior);
    }

    @Transactional(readOnly = true)
    public byte[] leerBytes(long idUsuario) {
        exigirCuentaActiva(idUsuario);
        MetaRef actual = leerMeta(idUsuario);
        if (actual == null || actual.objectKey() == null || actual.objectKey().isBlank()) {
            throw FotoException.notFound("No hay foto de perfil.");
        }
        if (!RutasFotoPersonalizada.esClavePerfilDe(actual.objectKey(), idUsuario)) {
            throw FotoException.forbidden("Ruta de foto no válida.");
        }
        try {
            return almacenamiento.leer(actual.objectKey());
        } catch (RuntimeException ex) {
            throw FotoException.storage("No se pudo obtener la foto.");
        }
    }

    @Transactional(readOnly = true)
    public MetaFoto metadatos(long idUsuario) {
        exigirCuentaActiva(idUsuario);
        MetaRef actual = leerMeta(idUsuario);
        if (actual == null || actual.objectKey() == null || actual.objectKey().isBlank()) {
            throw FotoException.notFound("No hay foto de perfil.");
        }
        long tamano = actual.tamanoBytes() == null ? 0L : actual.tamanoBytes();
        return new MetaFoto(actual.contentType(), tamano);
    }

    private void exigirCuentaActiva(long idUsuario) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT 1 FROM mycar.usuario WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                """, idUsuario);
        if (rows.isEmpty()) {
            throw FotoException.notFound("No se encontró el perfil.");
        }
    }

    private MetaRef leerMeta(long idUsuario) {
        List<MetaRef> rows = jdbc.query("""
                SELECT foto_storage_object_key, foto_content_type, foto_tamano_bytes
                  FROM mycar.usuario
                 WHERE id_usuario = ? AND estado_cuenta = 'ACTIVA'
                """, (rs, n) -> new MetaRef(
                rs.getString("foto_storage_object_key"),
                rs.getString("foto_content_type"),
                rs.getObject("foto_tamano_bytes") == null ? null : rs.getLong("foto_tamano_bytes")
        ), idUsuario);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void borrarSeguro(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            almacenamiento.borrarLogicoHuerfano(objectKey);
        } catch (RuntimeException ignored) {
            // Reintento trazable vía almacenamiento; no revertir referencia ya actualizada.
        }
    }

    private record MetaRef(String objectKey, String contentType, Long tamanoBytes) { }
}
