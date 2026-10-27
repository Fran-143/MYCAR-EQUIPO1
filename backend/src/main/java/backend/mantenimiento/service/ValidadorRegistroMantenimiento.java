package backend.mantenimiento.service;

import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public class ValidadorRegistroMantenimiento {

    private final Clock reloj;

    public ValidadorRegistroMantenimiento(Clock reloj) {
        this.reloj = reloj;
    }

    public void validar(DatosRegistroMantenimiento datos) {
        if (datos == null) {
            throw new IllegalArgumentException(
                    "datos: el mantenimiento es obligatorio"
            );
        }

        if (datos.codigoTipoMantenimiento() == null
                || datos.codigoTipoMantenimiento().isBlank()) {
            throw new IllegalArgumentException(
                    "codigoTipoMantenimiento: es obligatorio"
            );
        }

        if (datos.fechaServicio() == null) {
            throw new IllegalArgumentException(
                    "fechaServicio: es obligatoria"
            );
        }

        LocalDate hoy = LocalDate.now(
                reloj.withZone(ZoneId.of("America/Lima"))
        );

        if (datos.fechaServicio().isAfter(hoy)) {
            throw new IllegalArgumentException(
                    "fechaServicio: no puede ser futura"
            );
        }

        if (datos.kmServicio() == null || datos.kmServicio() <= 0) {
            throw new IllegalArgumentException(
                    "kmServicio: es obligatorio y debe ser mayor que cero"
            );
        }

        if (datos.origenKmServicio() == null) {
            throw new IllegalArgumentException(
                    "origenKmServicio: es obligatorio"
            );
        }

        if (datos.origenKmServicio() == OrigenKmServicio.LECTURA
                && datos.idLecturaKmReferencia() == null) {
            throw new IllegalArgumentException(
                    "idLecturaKmReferencia: es obligatoria para el origen LECTURA"
            );
        }

        if (datos.origenKmServicio() == OrigenKmServicio.HISTORICO_MANUAL
                && datos.idLecturaKmReferencia() != null) {
            throw new IllegalArgumentException(
                    "idLecturaKmReferencia: debe estar ausente para HISTORICO_MANUAL"
            );
        }

        if ("OTRO".equals(datos.codigoTipoMantenimiento())) {
            if (datos.servicioOtro() == null
                    || datos.servicioOtro().isBlank()) {
                throw new IllegalArgumentException(
                        "servicioOtro: es obligatorio cuando el tipo es OTRO"
                );
            }
        } else if (datos.servicioOtro() != null) {
            throw new IllegalArgumentException(
                    "servicioOtro: solo se permite para el tipo OTRO"
            );
        }

        if (datos.costoTotal() != null
                && datos.costoTotal().signum() < 0) {
            throw new IllegalArgumentException(
                    "costoTotal: no puede ser negativo"
            );
        }

        if (datos.proximaFechaSugerida() != null
                && !datos.proximaFechaSugerida()
                .isAfter(datos.fechaServicio())) {
            throw new IllegalArgumentException(
                    "proximaFechaSugerida: debe ser posterior a la fecha del servicio"
            );
        }

        if (datos.proximoKmSugerido() != null
                && datos.proximoKmSugerido() <= datos.kmServicio()) {
            throw new IllegalArgumentException(
                    "proximoKmSugerido: debe ser mayor que el kilometraje del servicio"
            );
        }
    }
}
