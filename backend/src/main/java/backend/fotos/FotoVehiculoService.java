package backend.fotos;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FotoVehiculoService {
    private final JdbcTemplate jdbc;
    private final AlmacenamientoFotos almacenamiento;

    public FotoVehiculoService(JdbcTemplate jdbc, AlmacenamientoFotos almacenamiento) {
        this.jdbc = jdbc;
        this.almacenamiento = almacenamiento;
    }

    public record MetaFoto(String contentType, long tamanoBytes) { }

    @Transactional
    public void registrar(long idUsuario, long idVehiculo, byte[] bytes, String contentTypeDeclarado) {
        VehiculoRef ref = exigirPropio(idUsuario, idVehiculo);
        exigirActivoParaMutacion(ref);
        if (ref.objectKey() != null && !ref.objectKey().isBlank()) {
            throw FotoException.conflict("Este vehículo ya tiene imagen. Usa cambiar foto.");
        }
        var validada = ValidadorFotoPersonalizada.validarYLimpiar(bytes, contentTypeDeclarado);
        String nuevaClave = RutasFotoPersonalizada.claveVehiculo(idUsuario, idVehiculo, validada.extension());
        RutasFotoPersonalizada.exigirClaveValida(nuevaClave);
        try {
            almacenamiento.guardar(nuevaClave, validada.bytesSinExif(), validada.contentType());
        } catch (RuntimeException ex) {
            throw FotoException.storage("No se pudo guardar la imagen. Intenta de nuevo.");
        }
        int updated = jdbc.update("""
                UPDATE mycar.vehiculo
                   SET foto_storage_object_key = ?,
                       foto_content_type = ?,
                       foto_tamano_bytes = ?,
                       foto_actualizada_en = NOW()
                 WHERE id_usuario = ? AND id_vehiculo = ? AND estado_vehiculo = 'ACTIVO'
                   AND (foto_storage_object_key IS NULL OR foto_storage_object_key = '')
                """, nuevaClave, validada.contentType(), (long) validada.bytesSinExif().length,
                idUsuario, idVehiculo);
        if (updated != 1) {
            borrarSeguro(nuevaClave);
            throw FotoException.conflict("Este vehículo ya tiene imagen. Usa cambiar foto.");
        }
    }

    @Transactional
    public void actualizar(long idUsuario, long idVehiculo, byte[] bytes, String contentTypeDeclarado) {
        VehiculoRef ref = exigirPropio(idUsuario, idVehiculo);
        exigirActivoParaMutacion(ref);
        if (ref.objectKey() == null || ref.objectKey().isBlank()) {
            throw FotoException.notFound("No hay imagen del vehículo para actualizar.");
        }
        String anterior = ref.objectKey();
        var validada = ValidadorFotoPersonalizada.validarYLimpiar(bytes, contentTypeDeclarado);
        String nuevaClave = RutasFotoPersonalizada.claveVehiculo(idUsuario, idVehiculo, validada.extension());
        RutasFotoPersonalizada.exigirClaveValida(nuevaClave);
        try {
            almacenamiento.guardar(nuevaClave, validada.bytesSinExif(), validada.contentType());
        } catch (RuntimeException ex) {
            throw FotoException.storage("No se pudo guardar la imagen. Intenta de nuevo.");
        }
        int updated = jdbc.update("""
                UPDATE mycar.vehiculo
                   SET foto_storage_object_key = ?,
                       foto_content_type = ?,
                       foto_tamano_bytes = ?,
                       foto_actualizada_en = NOW()
                 WHERE id_usuario = ? AND id_vehiculo = ? AND estado_vehiculo = 'ACTIVO'
                   AND foto_storage_object_key = ?
                """, nuevaClave, validada.contentType(), (long) validada.bytesSinExif().length,
                idUsuario, idVehiculo, anterior);
        if (updated != 1) {
            borrarSeguro(nuevaClave);
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }
        borrarSeguro(anterior);
    }

    @Transactional
    public void eliminar(long idUsuario, long idVehiculo) {
        VehiculoRef ref = exigirPropio(idUsuario, idVehiculo);
        exigirActivoParaMutacion(ref);
        if (ref.objectKey() == null || ref.objectKey().isBlank()) {
            throw FotoException.notFound("No hay imagen del vehículo para eliminar.");
        }
        String anterior = ref.objectKey();
        int updated = jdbc.update("""
                UPDATE mycar.vehiculo
                   SET foto_storage_object_key = NULL,
                       foto_content_type = NULL,
                       foto_tamano_bytes = NULL,
                       foto_actualizada_en = NULL
                 WHERE id_usuario = ? AND id_vehiculo = ? AND estado_vehiculo = 'ACTIVO'
                   AND foto_storage_object_key = ?
                """, idUsuario, idVehiculo, anterior);
        if (updated != 1) {
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }
        borrarSeguro(anterior);
    }

    @Transactional(readOnly = true)
    public byte[] leerBytes(long idUsuario, long idVehiculo) {
        VehiculoRef ref = exigirPropio(idUsuario, idVehiculo);
        if (ref.objectKey() == null || ref.objectKey().isBlank()) {
            throw FotoException.notFound("No hay imagen del vehículo.");
        }
        if (!RutasFotoPersonalizada.esClaveVehiculoDe(ref.objectKey(), idUsuario, idVehiculo)) {
            throw FotoException.forbidden("Ruta de imagen no válida.");
        }
        try {
            return almacenamiento.leer(ref.objectKey());
        } catch (RuntimeException ex) {
            throw FotoException.storage("No se pudo obtener la imagen.");
        }
    }

    @Transactional(readOnly = true)
    public MetaFoto metadatos(long idUsuario, long idVehiculo) {
        VehiculoRef ref = exigirPropio(idUsuario, idVehiculo);
        if (ref.objectKey() == null || ref.objectKey().isBlank()) {
            throw FotoException.notFound("No hay imagen del vehículo.");
        }
        long tamano = ref.tamanoBytes() == null ? 0L : ref.tamanoBytes();
        return new MetaFoto(ref.contentType(), tamano);
    }

    private VehiculoRef exigirPropio(long idUsuario, long idVehiculo) {
        List<VehiculoRef> rows = jdbc.query("""
                SELECT estado_vehiculo, foto_storage_object_key, foto_content_type, foto_tamano_bytes
                  FROM mycar.vehiculo
                 WHERE id_usuario = ? AND id_vehiculo = ?
                """, (rs, n) -> new VehiculoRef(
                rs.getString("estado_vehiculo"),
                rs.getString("foto_storage_object_key"),
                rs.getString("foto_content_type"),
                rs.getObject("foto_tamano_bytes") == null ? null : rs.getLong("foto_tamano_bytes")
        ), idUsuario, idVehiculo);
        if (rows.isEmpty()) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        return rows.get(0);
    }

    private static void exigirActivoParaMutacion(VehiculoRef ref) {
        if (!"ACTIVO".equals(ref.estado())) {
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }
    }

    private void borrarSeguro(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            almacenamiento.borrarLogicoHuerfano(objectKey);
        } catch (RuntimeException ignored) {
        }
    }

    private record VehiculoRef(String estado, String objectKey, String contentType, Long tamanoBytes) { }
}
