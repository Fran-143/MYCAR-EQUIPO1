package backend.soat.service;

import backend.crypto.DatosSensiblesCryptoGate;
import backend.documentos.AlmacenamientoDocumentos;
import backend.documentos.ErrorAlmacenamiento;
import backend.documentos.RutasDocumentoSoat;
import backend.notificaciones.service.NotificacionInternaService;
import backend.soat.api.dto.ContratoSoat;
import backend.soat.dao.SoatJdbcDao;
import backend.soat.model.ErrorSoat;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
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
    private final DatosSensiblesCryptoGate cryptoGate;
    private final ObjectMapper json;
    private final ObjectProvider<NotificacionInternaService> notificaciones;

    public SoatService(SoatJdbcDao dao, OperacionVehiculoDao operaciones,
            AlmacenamientoDocumentos almacenamiento, DatosSensiblesCryptoGate cryptoGate,
            ObjectMapper json, ObjectProvider<NotificacionInternaService> notificaciones) {
        this.dao = dao;
        this.operaciones = operaciones;
        this.almacenamiento = almacenamiento;
        this.cryptoGate = cryptoGate;
        this.json = json;
        this.notificaciones = notificaciones;
    }

    @Transactional(readOnly = true)
    public ContratoSoat.ConsultaResponse consultar(long usuario, long vehiculo) {
        exigirTitularidadConsulta(usuario, vehiculo);
        LocalDate hoy = dao.hoyLima();
        var actualOpt = dao.obtenerActual(vehiculo).map(f -> dtoPublico(f, hoy));
        String estado = actualOpt.map(ContratoSoat.SoatDto::estadoCobertura).orElse(null);
        var historial = dao.listarHistorial(vehiculo).stream()
                .map(f -> dtoPublico(f, hoy)).toList();
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
        String polizaBlind = blindDePoliza(req.numeroPoliza());
        if (dao.duplicadoContextual(vehiculo, req.aseguradora(), polizaBlind,
                req.fechaInicio(), req.fechaVencimiento(), null)) {
            throw new ErrorSoat("SOAT_DUPLICADO", "Ya existe un SOAT equivalente en ese período.");
        }
        if (dao.polizaYaRegistrada(polizaBlind, null)) {
            throw new ErrorSoat("SOAT_POLIZA_DUPLICADA",
                    "Este número de póliza ya está registrado",
                    Set.of("numeroPoliza"));
        }
        if (dao.aseguradoraPrimaDuplicada(vehiculo, req.aseguradora(), req.importePrima(), null)) {
            throw new ErrorSoat("SOAT_DUPLICADO_PRIMA", "Ya existe registrado",
                    Set.of("aseguradora", "importePrima"));
        }
        var doc = resolverDocumentoNuevo(usuario, vehiculo, null, req.documento());
        var sensibles = sellarCampos(vehiculo, null, req.numeroPoliza(), blancoANull(req.nombreContratante()));
        try {
            long idSoat = dao.insertar(new SoatJdbcDao.DatosInsercion(
                    vehiculo, req.aseguradora().strip(), sensibles.polizaCifrada(), sensibles.blind(), sensibles.keyId(),
                    req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(),
                    sensibles.nombreCifrado(), req.importePrima(),
                    "ACTUAL", false,
                    doc == null ? null : doc.objectKey(), doc == null ? null : doc.contentType(),
                    doc == null ? null : doc.tamanoBytes(), doc == null ? null : doc.nombreOriginal(),
                    documentoEstaCifrado(doc)));
            resellarTrasAlta(idSoat, vehiculo, req.numeroPoliza(), blancoANull(req.nombreContratante()), sensibles.blind());
            LocalDate hoy = dao.hoyLima();
            String cobertura = ValidadorSoat.estadoCobertura(req.fechaInicio(), req.fechaVencimiento(), hoy);
            var resp = new ContratoSoat.RegistrarResponse(req.idOperacion(), idSoat, cobertura);
            confirmarOperacion(req.idOperacion(), usuario, TIPO_REGISTRAR, huella, resp);
            return resp;
        } catch (DataIntegrityViolationException ex) {
            throw new ErrorSoat("SOAT_ACTUAL_EXISTE", "Este vehículo ya tiene un SOAT vigente documental.");
        } catch (ErrorSoat ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new ErrorSoat("SOAT_GUARDAR_FALLIDO", "No pudimos guardar el SOAT. Inténtalo de nuevo");
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
        String polizaBlind = blindDePoliza(req.numeroPoliza());
        if (dao.duplicadoContextual(vehiculo, req.aseguradora(), polizaBlind,
                req.fechaInicio(), req.fechaVencimiento(), idSoat)) {
            throw new ErrorSoat("SOAT_DUPLICADO", "Ya existe un SOAT equivalente en ese período.");
        }
        if (dao.polizaYaRegistrada(polizaBlind, idSoat)) {
            throw new ErrorSoat("SOAT_POLIZA_DUPLICADA",
                    "Este número de póliza ya está registrado",
                    Set.of("numeroPoliza"));
        }
        if (dao.aseguradoraPrimaDuplicada(vehiculo, req.aseguradora(), req.importePrima(), idSoat)) {
            throw new ErrorSoat("SOAT_DUPLICADO_PRIMA", "Ya existe registrado",
                    Set.of("aseguradora", "importePrima"));
        }
        DocumentoResuelto doc = resolverDocumentoCorregir(usuario, vehiculo, actual, req);
        if (sinCambiosCorregir(actual, req, doc)) {
            throw new ErrorSoat("SIN_CAMBIOS", "No hay cambios para guardar.");
        }
        String antes = serializar(actual);
        var sensibles = sellarCampos(vehiculo, idSoat, req.numeroPoliza(), blancoANull(req.nombreContratante()));
        int filas = dao.actualizarCorregir(new SoatJdbcDao.DatosActualizacion(
                idSoat, vehiculo, actual.versionFila(),
                req.aseguradora().strip(), sensibles.polizaCifrada(), sensibles.blind(), sensibles.keyId(),
                req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(),
                sensibles.nombreCifrado(), req.importePrima(),
                doc.objectKey(), doc.contentType(), doc.tamanoBytes(), doc.nombreOriginal(),
                doc.documentoCifrado()));
        if (filas != 1) throw new ErrorSoat("CONFLICTO_VERSION", "El SOAT cambió. Vuelve a cargar.");
        // Solo tras COMMIT lógico de la fila: limpiar objeto sustituido/quitado (conservar si falla).
        limpiarDocumentoAnteriorSiCorresponde(actual.storageObjectKey(), doc.objectKey(), req.accionDocumento());
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
        ValidadorSoat.exigirPuedeRenovar(anterior.fechaVencimiento(), dao.hoyLima());
        ValidadorSoat.exigirFechasRenovacion(
                req.fechaInicio(), req.fechaVencimiento(),
                anterior.fechaInicio(), anterior.fechaVencimiento());
        if (dao.solapeConHistorico(vehiculo, req.fechaInicio(), req.fechaVencimiento(), req.idSoatAnterior())) {
            throw new ErrorSoat("SOAT_SOLAPE",
                    "La nueva vigencia no puede empezar antes de que termine la actual.");
        }
        String polizaBlind = blindDePoliza(req.numeroPoliza());
        if (dao.duplicadoContextual(vehiculo, req.aseguradora(), polizaBlind,
                req.fechaInicio(), req.fechaVencimiento(), null)) {
            throw new ErrorSoat("SOAT_DUPLICADO", "Ya existe un SOAT equivalente en ese período.");
        }
        if (dao.polizaYaRegistrada(polizaBlind, req.idSoatAnterior())) {
            throw new ErrorSoat("SOAT_POLIZA_DUPLICADA",
                    "Este número de póliza ya está registrado",
                    Set.of("numeroPoliza"));
        }
        if (dao.aseguradoraPrimaDuplicada(vehiculo, req.aseguradora(), req.importePrima(), req.idSoatAnterior())) {
            throw new ErrorSoat("SOAT_DUPLICADO_PRIMA", "Ya existe registrado",
                    Set.of("aseguradora", "importePrima"));
        }
        boolean recordatorio = anterior.recordatorioActivo();
        cerrarAlertaPorRenovacion(anterior.idSoat());
        int hist = dao.marcarHistorico(anterior.idSoat(), vehiculo, anterior.versionFila());
        if (hist != 1) throw new ErrorSoat("CONFLICTO_VERSION", "No se pudo archivar el SOAT anterior.");
        var doc = resolverDocumentoNuevo(usuario, vehiculo, null, req.documento());
        var sensibles = sellarCampos(vehiculo, null, req.numeroPoliza(), blancoANull(req.nombreContratante()));
        long idNuevo = dao.insertar(new SoatJdbcDao.DatosInsercion(
                vehiculo, req.aseguradora().strip(), sensibles.polizaCifrada(), sensibles.blind(), sensibles.keyId(),
                req.fechaInicio(), req.fechaVencimiento(), req.fechaEmision(),
                sensibles.nombreCifrado(), req.importePrima(),
                "ACTUAL", recordatorio,
                doc == null ? null : doc.objectKey(), doc == null ? null : doc.contentType(),
                doc == null ? null : doc.tamanoBytes(), doc == null ? null : doc.nombreOriginal(),
                documentoEstaCifrado(doc)));
        resellarTrasAlta(idNuevo, vehiculo, req.numeroPoliza(), blancoANull(req.nombreContratante()), sensibles.blind());
        if (recordatorio) {
            long idAlerta = activarAlerta(idNuevo, vehiculo, req.fechaVencimiento());
            sincronizarNotificacion(idAlerta);
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
            // RF32-EA02: SOAT ya vencido → alerta VENCIDA para seguimiento en Alertas,
            // sin crear notificación nueva en Pendientes (PROXIMA/PENDIENTE sí sincronizan).
            if (!"VENCIDA".equals(estadoAlerta)) {
                sincronizarNotificacion(idAlerta);
            }
        } else {
            Long idAnulada = desactivarAlertaSiActiva(idSoat);
            if (idAnulada != null) {
                cerrarNotificacion(idAnulada);
            }
        }
        return new ContratoSoat.RecordatorioResponse(idSoat, req.activo(), idAlerta, estadoAlerta);
    }

    @Transactional(readOnly = true)
    public SoatJdbcDao.FilaSoat obtenerFilaDocumento(long usuario, long vehiculo, long idSoat) {
        exigirTitularidadConsulta(usuario, vehiculo);
        return dao.obtenerPropio(usuario, vehiculo, idSoat)
                .orElseThrow(() -> new ErrorSoat("SOAT_NO_ENCONTRADO", "SOAT no encontrado."));
    }

    @Transactional(readOnly = true)
    public byte[] leerDocumentoBytes(long usuario, long vehiculo, long idSoat) {
        var fila = obtenerFilaDocumento(usuario, vehiculo, idSoat);
        if (fila.storageObjectKey() == null) {
            throw new ErrorSoat("DOCUMENTO_NO_DISPONIBLE", "Este SOAT no tiene documento adjunto.");
        }
        try {
            byte[] almacenado = almacenamiento.leer(fila.storageObjectKey());
            String aad = aadDoc(fila.storageObjectKey());
            if (fila.documentoCifrado()) {
                try {
                    return cryptoGate.abrirBytes(almacenado, aad);
                } catch (RuntimeException ignorada) {
                    return almacenado;
                }
            }
            try {
                return cryptoGate.abrirBytes(almacenado, aad);
            } catch (RuntimeException ignorada) {
                return almacenado;
            }
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorSoat(ex.codigo(), ex.getMessage());
        }
    }

    @Transactional
    public ContratoSoat.DocumentoDto subirDocumentoAutenticado(
            long usuario, long vehiculo, String contentType, String nombreOriginal, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ErrorSoat("DATOS_INVALIDOS", "Archivo vacío.", Set.of("file"));
        }
        var solicitud = new ContratoSoat.PresignUploadRequest(contentType, bytes.length, nombreOriginal);
        ValidadorSoat.exigirPresign(solicitud);
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        cryptoGate.exigir();
        String nombre = RutasDocumentoSoat.nombreSeguro(nombreOriginal);
        String key = RutasDocumentoSoat.claveTemp(usuario, vehiculo, nombre);
        String ct = contentType == null ? "application/octet-stream" : contentType.strip();
        try {
            byte[] cifrado = cryptoGate.sellarBytes(bytes, aadDoc(key));
            almacenamiento.guardar(key, cifrado, ct);
            return new ContratoSoat.DocumentoDto(key, ct, bytes.length, nombreOriginal, true);
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
            Map<String, String> headers = presign.headers() == null ? Map.of() : presign.headers();
            return new ContratoSoat.PresignUploadResponse(key, presign.url(), presign.expiresAt(), headers);
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
                    actual.storageObjectKey(), actual.contentType(), actual.tamanoBytes(), actual.nombreOriginal(),
                    actual.documentoCifrado());
            case QUITAR -> new DocumentoResuelto(null, null, null, null, false);
            case REEMPLAZAR -> {
                ContratoSoat.DocumentoDto nuevo = resolverDocumentoNuevo(usuario, vehiculo, actual.idSoat(), req.documento());
                if (nuevo == null) {
                    yield new DocumentoResuelto(null, null, null, null, false);
                }
                yield new DocumentoResuelto(
                        nuevo.objectKey(), nuevo.contentType(), nuevo.tamanoBytes(), nuevo.nombreOriginal(),
                        documentoEstaCifrado(nuevo));
            }
        };
    }

    private void limpiarDocumentoAnteriorSiCorresponde(
            String claveAnterior, String claveNueva, ContratoSoat.AccionDocumento accion) {
        if (claveAnterior == null || claveAnterior.isBlank()) return;
        if (accion == ContratoSoat.AccionDocumento.MANTENER) return;
        if (accion == ContratoSoat.AccionDocumento.REEMPLAZAR
                && claveAnterior.equals(claveNueva)) {
            return;
        }
        try {
            almacenamiento.borrarLogicoHuerfano(claveAnterior);
        } catch (ErrorAlmacenamiento ex) {
            // La fila ya apunta al documento nuevo o a null; no revertir SOAT por basura de storage.
        }
    }

    private long activarAlerta(long idSoat, long vehiculo, LocalDate fechaVencimiento) {
        desactivarAlertaSiActiva(idSoat);
        LocalDate hoy = dao.hoyLima();
        LocalDate fechaBase = fechaVencimiento.minusDays(30);
        String firma = FirmaAlertaSoat.calcular(idSoat);
        String estado = dao.calcularEstadoInicial(fechaVencimiento, hoy);
        // ck_alerta_vencida_evidencia: VENCIDA exige fecha_vencida_verificable.
        // En alta inicial RF32 usamos hoy (primera evidencia del sistema), no objetivo+15
        // histórico, para no anular de inmediato el seguimiento de un SOAT ya vencido.
        LocalDate fechaVencidaVerificable = "VENCIDA".equals(estado) ? hoy : null;
        try {
            long idAlerta = dao.insertarAlertaSoat(
                    vehiculo, idSoat, fechaBase, fechaVencimiento, estado, firma, fechaVencidaVerificable);
            dao.historialAlertaInicial(idAlerta, estado);
            return idAlerta;
        } catch (DataIntegrityViolationException ex) {
            String detail = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage() : "";
            if (detail != null && (detail.contains("uq_alerta_soat_activa")
                    || detail.contains("uq_alerta_equivalente_activa"))) {
                throw new ErrorSoat("ALERTA_SOAT_DUPLICADA", "Ya existe un recordatorio activo para este SOAT.");
            }
            throw new ErrorSoat("SOAT_GUARDAR_FALLIDO", "No se pudo activar el recordatorio del SOAT.");
        }
    }

    /** @return id alerta anulada, o null */
    private Long desactivarAlertaSiActiva(long idSoat) {
        return dao.alertaActivaPorSoat(idSoat).map(alerta -> {
            var version = dao.alertaVersion(alerta.idAlerta()).orElseThrow();
            dao.anularAlertaSoat(version.idAlerta(), version.estado(), version.versionFila());
            return version.idAlerta();
        }).orElse(null);
    }

    private void cerrarAlertaPorRenovacion(long idSoatAnterior) {
        dao.alertaActivaPorSoat(idSoatAnterior).ifPresent(alerta -> {
            var version = dao.alertaVersion(alerta.idAlerta()).orElseThrow();
            dao.cerrarAlertaSoatPorRenovacion(version.idAlerta(), version.estado(), version.versionFila());
            var n = notificaciones.getIfAvailable();
            if (n != null) {
                n.marcarAtendidaPorAlerta(version.idAlerta());
            }
        });
    }

    private void reconciliarAlerta(long idSoat, long vehiculo, LocalDate fechaVencimiento) {
        if (dao.alertaActivaPorSoat(idSoat).isEmpty()) {
            return;
        }
        long idAlerta = activarAlerta(idSoat, vehiculo, fechaVencimiento);
        sincronizarNotificacion(idAlerta);
    }

    private void sincronizarNotificacion(long idAlerta) {
        var n = notificaciones.getIfAvailable();
        if (n != null) {
            n.crearOActualizarSegunEstadoAlerta(idAlerta);
        }
    }

    private void cerrarNotificacion(long idAlerta) {
        var n = notificaciones.getIfAvailable();
        if (n != null) {
            n.desactivarAvisosPendientesAlertaAnulada(idAlerta);
        }
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
            var claro = descifrarFila(fila);
            var snap = new java.util.LinkedHashMap<String, Object>();
            snap.put("idSoat", claro.idSoat());
            snap.put("aseguradora", claro.aseguradora());
            snap.put("numeroPoliza", mascarPoliza(claro.numeroPoliza()));
            snap.put("fechaInicio", claro.fechaInicio());
            snap.put("fechaVencimiento", claro.fechaVencimiento());
            snap.put("fechaEmision", claro.fechaEmision());
            snap.put("importePrima", claro.importePrima());
            snap.put("vigencia", claro.vigencia());
            snap.put("recordatorioActivo", claro.recordatorioActivo());
            snap.put("tieneDocumento", claro.storageObjectKey() != null && !claro.storageObjectKey().isBlank());
            snap.put("versionFila", claro.versionFila());
            return json.writeValueAsString(snap);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar SOAT", ex);
        }
    }

    private static String blancoANull(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return texto.strip();
    }

    private boolean sinCambiosCorregir(
            SoatJdbcDao.FilaSoat actual, ContratoSoat.CorregirRequest req, DocumentoResuelto doc) {
        if (req.accionDocumento() != ContratoSoat.AccionDocumento.MANTENER
                && req.accionDocumento() != ContratoSoat.AccionDocumento.QUITAR
                && req.accionDocumento() != ContratoSoat.AccionDocumento.REEMPLAZAR) {
            return false;
        }
        var claro = descifrarFila(actual);
        boolean mismosDatos = Objects.equals(norm(claro.aseguradora()), norm(req.aseguradora()))
                && Objects.equals(norm(claro.numeroPoliza()), norm(req.numeroPoliza()))
                && Objects.equals(claro.fechaInicio(), req.fechaInicio())
                && Objects.equals(claro.fechaVencimiento(), req.fechaVencimiento())
                && Objects.equals(claro.fechaEmision(), req.fechaEmision())
                && Objects.equals(blancoANull(claro.nombreContratante()), blancoANull(req.nombreContratante()))
                && Objects.equals(claro.importePrima(), req.importePrima());
        if (!mismosDatos) return false;
        return switch (req.accionDocumento()) {
            case MANTENER -> Objects.equals(actual.storageObjectKey(), doc.objectKey())
                    && actual.documentoCifrado() == doc.documentoCifrado();
            case QUITAR -> actual.storageObjectKey() == null;
            case REEMPLAZAR -> Objects.equals(actual.storageObjectKey(), doc.objectKey())
                    && actual.documentoCifrado() == doc.documentoCifrado();
        };
    }

    private static String norm(String valor) {
        return valor == null ? null : valor.strip();
    }

    private ContratoSoat.SoatDto dtoPublico(SoatJdbcDao.FilaSoat fila, LocalDate hoy) {
        return SoatJdbcDao.aDto(descifrarFila(fila), hoy);
    }

    private SoatJdbcDao.FilaSoat descifrarFila(SoatJdbcDao.FilaSoat fila) {
        String poliza = abrirCampoSoat(fila.numeroPoliza(), aadPoliza(fila.idSoat()), aadPolizaVehiculo(fila.idVehiculo()));
        String nombre = abrirCampoSoat(
                fila.nombreContratante(), aadContratante(fila.idSoat()), aadContratanteVehiculo(fila.idVehiculo()));
        return new SoatJdbcDao.FilaSoat(
                fila.idSoat(), fila.idVehiculo(), fila.aseguradora(), poliza, fila.numeroPolizaBlind(),
                fila.cryptoKeyId(), fila.fechaInicio(), fila.fechaVencimiento(), fila.fechaEmision(), nombre,
                fila.importePrima(), fila.vigencia(), fila.recordatorioActivo(), fila.versionFila(),
                fila.storageObjectKey(), fila.contentType(), fila.tamanoBytes(), fila.nombreOriginal(),
                fila.documentoCifrado());
    }

    /** Alta usa AAD por vehículo hasta tener id; lectura acepta ambos. */
    private String abrirCampoSoat(String valor, String aadId, String aadVehiculo) {
        try {
            return cryptoGate.abrirCampo(valor, aadId);
        } catch (RuntimeException primera) {
            try {
                return cryptoGate.abrirCampo(valor, aadVehiculo);
            } catch (RuntimeException ignorada) {
                throw primera;
            }
        }
    }

    private void resellarTrasAlta(long idSoat, long vehiculo, String poliza, String nombre, String blind) {
        var canonicos = sellarCampos(vehiculo, idSoat, poliza, nombre);
        dao.reemplazarCifrado(idSoat, canonicos.polizaCifrada(), canonicos.nombreCifrado(),
                blind != null ? blind : canonicos.blind(), canonicos.keyId());
    }

    private CamposSellados sellarCampos(long vehiculo, Long idSoat, String poliza, String nombre) {
        var crypto = cryptoGate.exigir();
        String polizaPlano = poliza.strip();
        String blind = crypto.indiceCiego(polizaPlano.replaceAll("[^0-9]", ""));
        String polizaAad = idSoat == null ? "soat|poliza|v|" + vehiculo : aadPoliza(idSoat);
        String nombreAad = idSoat == null ? "soat|contratante|v|" + vehiculo : aadContratante(idSoat);
        String polizaCifrada = crypto.sellar(polizaPlano, polizaAad);
        String nombreCifrado = nombre == null ? null : crypto.sellar(nombre, nombreAad);
        return new CamposSellados(polizaCifrada, nombreCifrado, blind, crypto.keyId());
    }

    private String blindDePoliza(String poliza) {
        if (poliza == null || poliza.isBlank()) {
            return null;
        }
        return cryptoGate.indiceCiego(poliza.strip().replaceAll("[^0-9]", ""));
    }

    private static boolean documentoEstaCifrado(ContratoSoat.DocumentoDto doc) {
        return doc != null && Boolean.TRUE.equals(doc.cifrado());
    }

    private static String aadPoliza(long idSoat) {
        return "soat|poliza|" + idSoat;
    }

    private static String aadPolizaVehiculo(long idVehiculo) {
        return "soat|poliza|v|" + idVehiculo;
    }

    private static String aadContratante(long idSoat) {
        return "soat|contratante|" + idSoat;
    }

    private static String aadContratanteVehiculo(long idVehiculo) {
        return "soat|contratante|v|" + idVehiculo;
    }

    private static String aadDoc(String objectKey) {
        return "soat|doc|" + objectKey;
    }

    private static String mascarPoliza(String poliza) {
        if (poliza == null || poliza.isBlank()) {
            return null;
        }
        String digitos = poliza.replaceAll("[^0-9]", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "****" + digitos.substring(digitos.length() - 4);
    }

    private record CamposSellados(String polizaCifrada, String nombreCifrado, String blind, String keyId) { }

    private record DocumentoResuelto(
            String objectKey, String contentType, Integer tamanoBytes, String nombreOriginal, boolean documentoCifrado
    ) { }
}