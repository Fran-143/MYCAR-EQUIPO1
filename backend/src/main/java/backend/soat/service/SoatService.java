package backend.soat.service;

import backend.documentos.AlmacenamientoDocumentos;
import backend.documentos.ErrorAlmacenamiento;
import backend.documentos.RutasDocumentoSoat;
import backend.soat.api.dto.ContratoSoat;
import backend.soat.dao.SoatJdbcDao;
import backend.soat.model.ErrorSoat;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SoatService {
    private static final String TIPO_REGISTRAR = "REGISTRAR_SOAT";
    private static final String TIPO_RENOVAR = "RENOVAR_SOAT";

    private final SoatJdbcDao dao;
    private final OperacionVehiculoDao operaciones;
    private final AlmacenamientoDocumentos almacenamiento;
    private final ObjectMapper json;

    public SoatService(SoatJdbcDao dao, OperacionVehiculoDao operaciones,
            AlmacenamientoDocumentos almacenamiento, ObjectMapper json) {
        this.dao = dao;
        this.operaciones = operaciones;
        this.almacenamiento = almacenamiento;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ContratoSoat.ConsultaResponse consultar(long usuario, long vehiculo) {
        exigirTitularidadConsulta(usuario, vehiculo);
        LocalDate hoy = dao.hoyLima();
        var actualOpt = dao.obtenerActual(vehiculo).map(SoatJdbcDao::aDto);
        String estado = actualOpt.map(a -> ValidadorSoat.estadoCobertura(
                a.fechaInicio(), a.fechaVencimiento(), hoy)).orElse(null);
        var historial = dao.listarHistorial(vehiculo).stream().map(SoatJdbcDao::aDto).toList();
        ContratoSoat.RecordatorioDto recordatorio = null;
        if (actualOpt.isPresent()) {
            var alerta = dao.alertaActivaPorSoat(actualOpt.get().idSoat()).orElse(null);
            recordatorio = new ContratoSoat.RecordatorioDto(
                    actualOpt.get().recordatorioActivo(),
                    alerta == null ? null : alerta.idAlerta(),
                    alerta == null ? null : alerta.estado());
        }
        return new ContratoSoat.ConsultaResponse(actualOpt.orElse(null), historial, estado, recordatorio);
    }

    @Transactional
    public ContratoSoat.RegistrarResponse registrar(long usuario, long vehiculo, ContratoSoat.RegistrarRequest req) {
        ValidadorSoat.exigirRegistro(req);
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        String huella = HuellaOperacionSoat.registrar(vehiculo, req);
        boolean nueva = operaciones.reservar(req.idOperacion(), usuario, TIPO_REGISTRAR, huella);
        var op = operaciones.buscarParaActualizar(req.idOperacion(), usuario, TIPO_REGISTRAR)
                .orElseThrow(() -> new ErrorSoat("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (!Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorSoat("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerRegistroConfirmado(op.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorSoat("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }
        dao.bloquearSoatVehiculo(vehiculo);
        if (dao.existeActual(vehiculo)) {
            throw new ErrorSoat("SOAT_ACTUAL_EXISTE", "Este vehículo ya tiene un SOAT vigente documental.");
        }
        if (dao.duplicadoContextual(vehiculo, req.aseguradora(), req.numeroPoliza(),
                req.fechaInicio(), req.fechaVencimiento(), null)) {
            throw new ErrorSoat("SOAT_DUPLICADO", "Ya existe un SOAT equivalente en ese período.");
        }
        var doc = resolverDocumentoNuevo(usuario, vehiculo, null, req.documento());
        try {
            long idSoat = dao.insertar(new SoatJdbcDao.DatosInsercion(
                    vehiculo, req.aseguradora().strip(), req.numeroPoliza().strip(),
                    req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(),
                    blancoANull(req.nombreContratante()), req.importePrima(),
                    "ACTUAL", false,
                    doc == null ? null : doc.objectKey(), doc == null ? null : doc.contentType(),
                    doc == null ? null : doc.tamanoBytes(), doc == null ? null : doc.nombreOriginal()));
            LocalDate hoy = dao.hoyLima();
            String cobertura = ValidadorSoat.estadoCobertura(req.fechaInicio(), req.fechaVencimiento(), hoy);
            var resp = new ContratoSoat.RegistrarResponse(req.idOperacion(), idSoat, cobertura);
            confirmarOperacion(req.idOperacion(), usuario, TIPO_REGISTRAR, huella, resp);
            return resp;
        } catch (DataIntegrityViolationException ex) {
            throw new ErrorSoat("SOAT_ACTUAL_EXISTE", "Este vehículo ya tiene un SOAT vigente documental.");
        }
    }

    @Transactional
    public ContratoSoat.CorregirResponse corregir(long usuario, long vehiculo, long idSoat,
            ContratoSoat.CorregirRequest req) {
        ValidadorSoat.exigirCorregir(req);
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        dao.bloquearSoatVehiculo(vehiculo);
        var actual = dao.obtenerPropio(usuario, vehiculo, idSoat)
                .filter(s -> "ACTUAL".equals(s.vigencia()))
                .orElseThrow(() -> new ErrorSoat("SOAT_NO_ENCONTRADO", "SOAT no encontrado."));
        if (dao.duplicadoContextual(vehiculo, req.aseguradora(), req.numeroPoliza(),
                req.fechaInicio(), req.fechaVencimiento(), idSoat)) {
            throw new ErrorSoat("SOAT_DUPLICADO", "Ya existe un SOAT equivalente en ese período.");
        }
        String antes = serializar(actual);
        DocumentoResuelto doc = resolverDocumentoCorregir(usuario, vehiculo, actual, req);
        int filas = dao.actualizarCorregir(new SoatJdbcDao.DatosActualizacion(
                idSoat, vehiculo, actual.versionFila(),
                req.aseguradora().strip(), req.numeroPoliza().strip(),
                req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(),
                blancoANull(req.nombreContratante()), req.importePrima(),
                doc.objectKey(), doc.contentType(), doc.tamanoBytes(), doc.nombreOriginal()));
        if (filas != 1) throw new ErrorSoat("CONFLICTO_VERSION", "El SOAT cambió. Vuelve a cargar.");
        var actualizado = dao.obtenerPropio(usuario, vehiculo, idSoat).orElseThrow();
        dao.insertarCambio(idSoat, usuario, antes, serializar(actualizado));
        if (actual.recordatorioActivo()) {
            reconciliarAlerta(idSoat, vehiculo, actualizado.fechaVencimiento());
        }
        LocalDate hoy = dao.hoyLima();
        return new ContratoSoat.CorregirResponse(
                idSoat, actualizado.versionFila(),
                ValidadorSoat.estadoCobertura(actualizado.fechaInicio(), actualizado.fechaVencimiento(), hoy));
    }

    @Transactional
    public ContratoSoat.RenovarResponse renovar(long usuario, long vehiculo, ContratoSoat.RenovarRequest req) {
        ValidadorSoat.exigirRenovar(req);
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        String huella = HuellaOperacionSoat.renovar(vehiculo, req);
        boolean nueva = operaciones.reservar(req.idOperacion(), usuario, TIPO_RENOVAR, huella);
        var op = operaciones.buscarParaActualizar(req.idOperacion(), usuario, TIPO_RENOVAR)
                .orElseThrow(() -> new ErrorSoat("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (!Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorSoat("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerRenovacionConfirmada(op.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorSoat("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }
        dao.bloquearSoatVehiculo(vehiculo);
        var anterior = dao.obtenerPropio(usuario, vehiculo, req.idSoatAnterior())
                .filter(s -> "ACTUAL".equals(s.vigencia()))
                .orElseThrow(() -> new ErrorSoat("SOAT_NO_ENCONTRADO", "SOAT anterior no encontrado."));
        if (!req.fechaInicio().isAfter(anterior.fechaInicio())
                && dao.solapeConHistorico(vehiculo, req.fechaInicio(), req.fechaVencimiento(), req.idSoatAnterior())) {
            throw new ErrorSoat("SOAT_SOLAPE", "Las fechas se solapan con otro SOAT.");
        }
        if (dao.duplicadoContextual(vehiculo, req.aseguradora(), req.numeroPoliza(),
                req.fechaInicio(), req.fechaVencimiento(), null)) {
            throw new ErrorSoat("SOAT_DUPLICADO", "Ya existe un SOAT equivalente en ese período.");
        }
        boolean recordatorio = anterior.recordatorioActivo();
        desactivarAlertaSiActiva(anterior.idSoat());
        int hist = dao.marcarHistorico(anterior.idSoat(), vehiculo, anterior.versionFila());
        if (hist != 1) throw new ErrorSoat("CONFLICTO_VERSION", "No se pudo archivar el SOAT anterior.");
        var doc = resolverDocumentoNuevo(usuario, vehiculo, null, req.documento());
        long idNuevo = dao.insertar(new SoatJdbcDao.DatosInsercion(
                vehiculo, req.aseguradora().strip(), req.numeroPoliza().strip(),
                req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(),
                blancoANull(req.nombreContratante()), req.importePrima(),
                "ACTUAL", recordatorio,
                doc == null ? null : doc.objectKey(), doc == null ? null : doc.contentType(),
                doc == null ? null : doc.tamanoBytes(), doc == null ? null : doc.nombreOriginal()));
        if (recordatorio) {
            activarAlerta(idNuevo, vehiculo, req.fechaVencimiento());
        }
        LocalDate hoy = dao.hoyLima();
        var resp = new ContratoSoat.RenovarResponse(
                req.idOperacion(), req.idSoatAnterior(), idNuevo,
                ValidadorSoat.estadoCobertura(req.fechaInicio(), req.fechaVencimiento(), hoy));
        confirmarOperacion(req.idOperacion(), usuario, TIPO_RENOVAR, huella, resp);
        return resp;
    }

    @Transactional
    public ContratoSoat.RecordatorioResponse configurarRecordatorio(
            long usuario, long vehiculo, long idSoat, ContratoSoat.RecordatorioRequest req) {
        if (req == null) throw new ErrorSoat("DATOS_INVALIDOS", "Solicitud vacía.");
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        dao.bloquearSoatVehiculo(vehiculo);
        var fila = dao.obtenerPropio(usuario, vehiculo, idSoat)
                .filter(s -> "ACTUAL".equals(s.vigencia()))
                .orElseThrow(() -> new ErrorSoat("SOAT_NO_ENCONTRADO", "SOAT no encontrado."));
        if (req.activo() == fila.recordatorioActivo()) {
            var alerta = dao.alertaActivaPorSoat(idSoat).orElse(null);
            return new ContratoSoat.RecordatorioResponse(
                    idSoat, fila.recordatorioActivo(),
                    alerta == null ? null : alerta.idAlerta(),
                    alerta == null ? null : alerta.estado());
        }
        int filas = dao.actualizarRecordatorio(idSoat, vehiculo, req.activo(), fila.versionFila());
        if (filas != 1) throw new ErrorSoat("CONFLICTO_VERSION", "El SOAT cambió. Vuelve a cargar.");
        Long idAlerta = null;
        String estadoAlerta = null;
        if (req.activo()) {
            idAlerta = activarAlerta(idSoat, vehiculo, fila.fechaVencimiento());
            estadoAlerta = dao.alertaActivaPorSoat(idSoat).map(SoatJdbcDao.AlertaSoat::estado).orElse(null);
        } else {
            desactivarAlertaSiActiva(idSoat);
        }
        return new ContratoSoat.RecordatorioResponse(idSoat, req.activo(), idAlerta, estadoAlerta);
    }

    @Transactional(readOnly = true)
    public ContratoSoat.DocumentoDescargaResponse descargarDocumento(long usuario, long vehiculo, long idSoat) {
        exigirTitularidadConsulta(usuario, vehiculo);
        var fila = dao.obtenerPropio(usuario, vehiculo, idSoat)
                .orElseThrow(() -> new ErrorSoat("SOAT_NO_ENCONTRADO", "SOAT no encontrado."));
        if (fila.storageObjectKey() == null) {
            throw new ErrorSoat("DOCUMENTO_NO_DISPONIBLE", "Este SOAT no tiene documento adjunto.");
        }
        try {
            var presign = almacenamiento.generarPresignDownload(fila.storageObjectKey());
            return new ContratoSoat.DocumentoDescargaResponse(
                    presign.url(), presign.expiresAt(), fila.nombreOriginal());
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorSoat(ex.codigo(), ex.getMessage());
        }
    }

    @Transactional
    public ContratoSoat.PresignUploadResponse presignUpload(
            long usuario, long vehiculo, ContratoSoat.PresignUploadRequest req) {
        ValidadorSoat.exigirPresign(req);
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        String nombre = RutasDocumentoSoat.nombreSeguro(req.nombreOriginal());
        String key = RutasDocumentoSoat.claveTemp(usuario, vehiculo, nombre);
        try {
            long max = ValidadorSoat.maximoPermitido(req.contentType());
            var presign = almacenamiento.generarPresignUpload(key, req.contentType(), max);
            return new ContratoSoat.PresignUploadResponse(key, presign.url(), presign.expiresAt());
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorSoat(ex.codigo(), ex.getMessage());
        }
    }

    private void exigirTitularidadConsulta(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorSoat("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        String estado = dao.estadoVehiculoPropio(usuario, vehiculo);
        if (estado == null) throw new ErrorSoat("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
    }

    private void exigirVehiculoActivoMutacion(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorSoat("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        try {
            dao.exigirVehiculoActivo(usuario, vehiculo);
        } catch (Exception ex) {
            String estado = dao.estadoVehiculoPropio(usuario, vehiculo);
            if (estado == null) throw new ErrorSoat("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
            if ("INACTIVO".equals(estado)) {
                throw new ErrorSoat("VEHICULO_INACTIVO", "Esta operación solo está disponible para vehículos activos.");
            }
            throw new ErrorSoat("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
    }

    private ContratoSoat.DocumentoDto resolverDocumentoNuevo(
            long usuario, long vehiculo, Long idSoat, ContratoSoat.DocumentoDto doc) {
        if (doc == null) return null;
        if (!RutasDocumentoSoat.perteneceAVehiculo(doc.objectKey(), usuario, vehiculo)) {
            throw new ErrorSoat("DOCUMENTO_NO_AUTORIZADO", "La ruta del documento no es válida.");
        }
        try {
            if (!almacenamiento.confirmarExistencia(doc.objectKey())) {
                throw new ErrorSoat("DOCUMENTO_NO_ENCONTRADO", "Sube el documento antes de confirmar.");
            }
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorSoat(ex.codigo(), ex.getMessage());
        }
        return doc;
    }

    private DocumentoResuelto resolverDocumentoCorregir(
            long usuario, long vehiculo, SoatJdbcDao.FilaSoat actual, ContratoSoat.CorregirRequest req) {
        return switch (req.accionDocumento()) {
            case MANTENER -> new DocumentoResuelto(
                    actual.storageObjectKey(), actual.contentType(), actual.tamanoBytes(), actual.nombreOriginal());
            case QUITAR -> {
                if (actual.storageObjectKey() != null) {
                    try {
                        almacenamiento.borrarLogicoHuerfano(actual.storageObjectKey());
                    } catch (ErrorAlmacenamiento ex) {
                        throw new ErrorSoat(ex.codigo(), ex.getMessage());
                    }
                }
                yield new DocumentoResuelto(null, null, null, null);
            }
            case REEMPLAZAR -> {
                ContratoSoat.DocumentoDto nuevo = resolverDocumentoNuevo(usuario, vehiculo, actual.idSoat(), req.documento());
                if (actual.storageObjectKey() != null && nuevo != null
                        && !actual.storageObjectKey().equals(nuevo.objectKey())) {
                    try {
                        almacenamiento.borrarLogicoHuerfano(actual.storageObjectKey());
                    } catch (ErrorAlmacenamiento ex) {
                        // Conservar documento anterior si falla storage
                        throw new ErrorSoat("STORAGE_NO_DISPONIBLE",
                                "No se pudo reemplazar el documento; se conservó el anterior.");
                    }
                }
                if (nuevo == null) {
                    yield new DocumentoResuelto(null, null, null, null);
                }
                yield new DocumentoResuelto(
                        nuevo.objectKey(), nuevo.contentType(), nuevo.tamanoBytes(), nuevo.nombreOriginal());
            }
        };
    }

    private long activarAlerta(long idSoat, long vehiculo, LocalDate fechaVencimiento) {
        desactivarAlertaSiActiva(idSoat);
        LocalDate hoy = dao.hoyLima();
        LocalDate fechaBase = fechaVencimiento.minusDays(30);
        String firma = FirmaAlertaSoat.calcular(idSoat);
        String estado = dao.calcularEstadoInicial(fechaVencimiento, hoy);
        try {
            long idAlerta = dao.insertarAlertaSoat(vehiculo, idSoat, fechaBase, fechaVencimiento, estado, firma);
            dao.historialAlertaInicial(idAlerta, estado);
            return idAlerta;
        } catch (DataIntegrityViolationException ex) {
            throw new ErrorSoat("ALERTA_SOAT_DUPLICADA", "Ya existe un recordatorio activo para este SOAT.");
        }
    }

    private void desactivarAlertaSiActiva(long idSoat) {
        dao.alertaActivaPorSoat(idSoat).ifPresent(alerta -> {
            var version = dao.alertaVersion(alerta.idAlerta()).orElseThrow();
            dao.anularAlertaSoat(version.idAlerta(), version.estado(), version.versionFila());
        });
    }

    private void reconciliarAlerta(long idSoat, long vehiculo, LocalDate fechaVencimiento) {
        if (dao.alertaActivaPorSoat(idSoat).isEmpty()) {
            return;
        }
        activarAlerta(idSoat, vehiculo, fechaVencimiento);
    }

    private void confirmarOperacion(UUID idOperacion, long usuario, String tipo, String huella, Object respuesta) {
        try {
            String payload = json.writeValueAsString(respuesta);
            if (!operaciones.confirmar(idOperacion, usuario, tipo, huella, payload)) {
                throw new ErrorSoat("OPERACION_EN_PROCESO", "No se pudo confirmar la operación.", true);
            }
        } catch (ErrorSoat ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar respuesta SOAT", ex);
        }
    }

    private ContratoSoat.RegistrarResponse leerRegistroConfirmado(String payload) {
        try {
            return json.readValue(payload, ContratoSoat.RegistrarResponse.class);
        } catch (Exception ex) {
            throw new ErrorSoat("OPERACION_EN_PROCESO", "Resultado pendiente.", true);
        }
    }

    private ContratoSoat.RenovarResponse leerRenovacionConfirmada(String payload) {
        try {
            return json.readValue(payload, ContratoSoat.RenovarResponse.class);
        } catch (Exception ex) {
            throw new ErrorSoat("OPERACION_EN_PROCESO", "Resultado pendiente.", true);
        }
    }

    private String serializar(SoatJdbcDao.FilaSoat fila) {
        try {
            return json.writeValueAsString(SoatJdbcDao.aDto(fila));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar SOAT", ex);
        }
    }

    private static String blancoANull(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return texto.strip();
    }

    private record DocumentoResuelto(String objectKey, String contentType, Integer tamanoBytes, String nombreOriginal) { }
}