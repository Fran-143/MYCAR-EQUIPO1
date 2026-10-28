package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import java.util.UUID;

final class HuellaProgramarAlerta {
    private HuellaProgramarAlerta() { }

    static String calcular(long vehiculo, ContratoRf22.ProgramarRequest req) {
        return vehiculo + "|" + req.codigoTipoMantenimiento() + "|" + req.criterio() + "|"
                + nullSafe(req.unidadTiempo()) + "|" + nullSafe(req.cantidadTiempo()) + "|"
                + nullSafe(req.intervaloKm()) + "|" + nullSafe(req.servicioOtro()) + "|"
                + nullSafe(req.idMantenimientoReferencia()) + "|" + req.idOperacion();
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }
}
