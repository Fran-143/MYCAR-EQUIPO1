package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Huella SHA-256 de 64 hex para operacion_idempotente.hash_solicitud. */
final class HuellaProgramarAlerta {
    private HuellaProgramarAlerta() { }

    static String calcular(long vehiculo, ContratoRf22.ProgramarRequest req) {
        String canon = "RF22_PROGRAMAR_ALERTA_V1\u001f" + vehiculo + '\u001f'
                + req.codigoTipoMantenimiento() + '\u001f' + req.criterio() + '\u001f'
                + nullSafe(req.unidadTiempo()) + '\u001f' + nullSafe(req.cantidadTiempo()) + '\u001f'
                + nullSafe(req.intervaloKm()) + '\u001f' + nullSafe(req.servicioOtro()) + '\u001f'
                + nullSafe(req.idMantenimientoReferencia()) + '\u001f' + req.idOperacion();
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular hash_solicitud de PROGRAMAR_ALERTA", ex);
        }
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }
}
