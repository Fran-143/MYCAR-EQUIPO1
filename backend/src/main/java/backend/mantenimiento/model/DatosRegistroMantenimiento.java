package backend.mantenimiento.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DatosRegistroMantenimiento(
        String codigoTipoMantenimiento,
        LocalDate fechaServicio,
        Integer kmServicio,
        OrigenKmServicio origenKmServicio,
        Long idLecturaKmReferencia,
        BigDecimal costoTotal,
        String servicioOtro,
        LocalDate proximaFechaSugerida,
        Integer proximoKmSugerido
) {
    public enum OrigenKmServicio {
        LECTURA,
        HISTORICO_MANUAL
    }
}