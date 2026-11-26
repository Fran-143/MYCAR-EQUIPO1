package backend.recordatorioskm.service;

import backend.recordatorioskm.api.dto.ContratoRecordatoriosKm;
import backend.recordatorioskm.dao.RecordatorioKmJdbcDao;
import backend.recordatorioskm.domain.FrecuenciaRecordatorioKm;
import backend.recordatorioskm.model.ErrorRecordatorioKm;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordatorioKmService {
    private static final DateTimeFormatter FECHA_UI =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);

    private final RecordatorioKmJdbcDao dao;

    public RecordatorioKmService(RecordatorioKmJdbcDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public ContratoRecordatoriosKm.ConfiguracionResponse consultar(long idUsuario) {
        String estado = dao.estadoGlobal(idUsuario);
        Instant ahora = Instant.now();
        var vehiculos = new ArrayList<ContratoRecordatoriosKm.VehiculoRecordatorioDto>();
        for (var row : dao.listarVehiculosActivos(idUsuario)) {
            vehiculos.add(mapear(row, ahora));
        }
        return new ContratoRecordatoriosKm.ConfiguracionResponse(estado, vehiculos);
    }

    @Transactional
    public ContratoRecordatoriosKm.ConfiguracionResponse actualizarGlobal(long idUsuario, String estadoRaw) {
        String estado = normalizarEstado(estadoRaw);
        dao.actualizarEstadoGlobal(idUsuario, estado);
        return consultar(idUsuario);
    }

    @Transactional
    public ContratoRecordatoriosKm.PreferenciaVehiculoResponse guardarFrecuencia(
            long idUsuario, long idVehiculo, String frecuenciaRaw) {
        var vehiculo = dao.vehiculoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRecordatorioKm(
                        "VEHICULO_NO_ENCONTRADO", "No se encontró el vehículo en tu cuenta."));
        if (!"ACTIVO".equals(vehiculo.estadoVehiculo())) {
            throw new ErrorRecordatorioKm(
                    "VEHICULO_INACTIVO",
                    "No puedes configurar recordatorios en un vehículo inactivo.");
        }
        var frecuencia = FrecuenciaRecordatorioKm.parse(frecuenciaRaw)
                .orElseThrow(() -> new ErrorRecordatorioKm(
                        "FRECUENCIA_INVALIDA",
                        "Selecciona Diario, Interdiario, Semanal, Quincenal o Mensual."));
        Instant fechaLectura = dao.fechaUltimaLectura(idVehiculo)
                .orElseThrow(() -> new ErrorRecordatorioKm(
                        "SIN_LECTURA_VALIDA",
                        "No hay una lectura de kilometraje válida para calcular el recordatorio."));
        LocalDate baseLima = RecordatorioKmJdbcDao.aFechaLima(fechaLectura);
        LocalDate proximoLima = frecuencia.calcularProximo(baseLima);
        Instant proximo = RecordatorioKmJdbcDao.inicioDiaLimaUtc(proximoLima);
        dao.guardarFrecuencia(idVehiculo, frecuencia, fechaLectura, proximo);
        Instant ahora = Instant.now();
        boolean pendiente = !proximo.isAfter(ahora);
        return new ContratoRecordatoriosKm.PreferenciaVehiculoResponse(
                idVehiculo,
                frecuencia.codigo(),
                FECHA_UI.format(proximoLima),
                false,
                pendiente);
    }

    /** RF14 / RF16: recalcular desde la fecha efectiva de la lectura válida. */
    @Transactional
    public void recalcularTrasLectura(long idVehiculo, Instant fechaLecturaEfectiva) {
        if (idVehiculo <= 0 || fechaLecturaEfectiva == null) {
            return;
        }
        String freq = dao.frecuenciaVehiculo(idVehiculo).orElse(null);
        if (freq == null) {
            return;
        }
        var frecuencia = FrecuenciaRecordatorioKm.parse(freq).orElse(null);
        if (frecuencia == null) {
            return;
        }
        LocalDate baseLima = RecordatorioKmJdbcDao.aFechaLima(fechaLecturaEfectiva);
        LocalDate proximoLima = frecuencia.calcularProximo(baseLima);
        Instant proximo = RecordatorioKmJdbcDao.inicioDiaLimaUtc(proximoLima);
        dao.recalcularTrasLectura(idVehiculo, fechaLecturaEfectiva, proximo);
    }

    private ContratoRecordatoriosKm.VehiculoRecordatorioDto mapear(
            RecordatorioKmJdbcDao.VehiculoConfigRow row, Instant ahora) {
        boolean sinLectura = row.fechaUltimaLectura() == null && row.kmActual() == null;
        Instant base = row.fechaUltimaLectura() != null ? row.fechaUltimaLectura() : row.fechaBase();
        String frecuencia = row.frecuencia() == null ? "INTERDIARIO" : row.frecuencia();
        String proximoTxt = null;
        boolean pendiente = false;
        Instant proximo = row.proximoEnvio();
        if (proximo == null && base != null) {
            var freq = FrecuenciaRecordatorioKm.parse(frecuencia).orElse(FrecuenciaRecordatorioKm.INTERDIARIO);
            LocalDate proximoLima = freq.calcularProximo(RecordatorioKmJdbcDao.aFechaLima(base));
            proximo = RecordatorioKmJdbcDao.inicioDiaLimaUtc(proximoLima);
            proximoTxt = FECHA_UI.format(proximoLima);
        } else if (proximo != null) {
            proximoTxt = FECHA_UI.format(RecordatorioKmJdbcDao.aFechaLima(proximo));
        }
        if (proximo != null && !proximo.isAfter(ahora)) {
            pendiente = true;
        }
        if (sinLectura) {
            proximoTxt = null;
            pendiente = false;
        }
        return new ContratoRecordatoriosKm.VehiculoRecordatorioDto(
                row.idVehiculo(),
                row.marca(),
                row.modelo(),
                row.placa(),
                row.tieneFoto(),
                row.kmActual(),
                frecuencia,
                proximoTxt,
                sinLectura,
                pendiente);
    }

    private static String normalizarEstado(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ErrorRecordatorioKm("DATOS_INVALIDOS", "Indica ACTIVO o PAUSADO.");
        }
        String e = raw.strip().toUpperCase(Locale.ROOT);
        if (!"ACTIVO".equals(e) && !"PAUSADO".equals(e)) {
            throw new ErrorRecordatorioKm("DATOS_INVALIDOS", "Indica ACTIVO o PAUSADO.");
        }
        return e;
    }
}
