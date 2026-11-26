package backend.historial.export;

import backend.historial.export.ExportarHistorialJdbcDao.CabeceraVehiculo;
import backend.historial.export.ExportarHistorialModelos.*;
import backend.planes.service.PlanesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class ExportarHistorialPdfService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final ExportarHistorialJdbcDao dao;
    private final PlanesService planes;

    public ExportarHistorialPdfService(ExportarHistorialJdbcDao dao, PlanesService planes) {
        this.dao = dao;
        this.planes = planes;
    }

    public record PdfGenerado(byte[] bytes, String nombreArchivo) { }

    @Transactional
    public ContextoExport contexto(long idUsuario, long idVehiculo) {
        boolean premium = planes.tienePremiumEfectivo(idUsuario);
        CabeceraVehiculo v = dao.vehiculoExportable(idUsuario, idVehiculo)
                .orElseThrow(() -> new ExportarHistorialError(
                        "VEHICULO_NO_DISPONIBLE", "El vehículo no está disponible"));
        List<TipoFiltro> tipos = dao.tiposParaVehiculo(idUsuario, idVehiculo);
        return new ContextoExport(
                premium, v.idVehiculo(), v.placa(), v.marca(), v.modelo(),
                v.estadoVehiculo(), v.kmVigente(), tipos);
    }

    @Transactional
    public PdfGenerado generar(long idUsuario, long idVehiculo, ExportarHistorialRequest req) {
        if (!planes.tienePremiumEfectivo(idUsuario)) {
            throw new ExportarHistorialError(
                    "PREMIUM_REQUERIDO",
                    "La exportación de historial está disponible solo para usuarios con plan Premium.");
        }
        CabeceraVehiculo v = dao.vehiculoExportable(idUsuario, idVehiculo)
                .orElseThrow(() -> new ExportarHistorialError(
                        "VEHICULO_NO_DISPONIBLE", "El vehículo no está disponible"));

        LocalDate hoy = LocalDate.now(LIMA);
        LocalDate desde = req == null ? null : req.fechaDesde();
        LocalDate hasta = req == null ? null : req.fechaHasta();
        validarFechas(desde, hasta, hoy);

        Long idTipo = req.tipoMantenimientoId();
        if (idTipo != null) {
            if (idTipo <= 0 || !dao.tipoAutorizado(idUsuario, idVehiculo, idTipo)) {
                throw new ExportarHistorialError(
                        "TIPO_INVALIDO", "El tipo de mantenimiento no es válido para este vehículo.");
            }
        }

        boolean incluirAnulados = req.incluirAnuladosEfectivo();
        List<MantenimientoExport> regs = dao.consultarExport(
                idUsuario, idVehiculo, desde, hasta, idTipo, incluirAnulados);

        CabeceraExport cab = new CabeceraExport(
                v.placa(), v.marca(), v.modelo(), v.kmVigente(), desde, hasta, hoy);
        byte[] pdf = ExportarHistorialPdfGenerator.generar(cab, regs);
        if (pdf == null || pdf.length == 0) {
            throw new ExportarHistorialError("PDF_NO_GENERADO", "No se pudo generar el PDF.");
        }
        return new PdfGenerado(pdf, nombreArchivo(v.placa(), desde, hasta));
    }

    static void validarFechas(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        if (desde == null || hasta == null) {
            throw new ExportarHistorialError("FECHAS_REQUERIDAS", "Debes indicar fecha Desde y Hasta.");
        }
        if (desde.isAfter(hoy) || hasta.isAfter(hoy)) {
            throw new ExportarHistorialError("FECHA_FUTURA", "No se permiten fechas futuras.");
        }
        if (hasta.isBefore(desde)) {
            throw new ExportarHistorialError("RANGO_INVALIDO", "La fecha Hasta debe ser igual o posterior a Desde.");
        }
        LocalDate max = desde.plusMonths(6);
        if (hasta.isAfter(max)) {
            throw new ExportarHistorialError(
                    "RANGO_EXCEDIDO", "El período máximo de exportación es de seis meses calendario.");
        }
    }

    static String nombreArchivo(String placa, LocalDate desde, LocalDate hasta) {
        String p = sane(placa == null ? "SINPLACA" : placa);
        return "Historial_MyCar_" + p + "_" + desde + "_" + hasta + ".pdf";
    }

    private static String sane(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
        return s.isEmpty() ? "SINPLACA" : s;
    }
}
