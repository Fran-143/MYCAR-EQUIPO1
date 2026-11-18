package backend.soporte.service;

import backend.documentos.AlmacenamientoDocumentos;
import backend.documentos.ErrorAlmacenamiento;
import backend.documentos.RutasDocumentoSoporte;
import backend.soporte.api.dto.ContratoSoporte;
import backend.soporte.dao.SoporteJdbcDao;
import backend.soporte.model.ErrorSoporte;
import backend.tive.dao.TiveJdbcDao;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SoporteService {
    private static final String TIPO = "CORRECCION_VIN";
    private static final String OP = "SOLICITAR_CORRECCION_VIN";

    private final SoporteJdbcDao dao;
    private final TiveJdbcDao tiveDao;
    private final OperacionVehiculoDao operaciones;
    private final AlmacenamientoDocumentos almacenamiento;
    private final ObjectMapper json;

    public SoporteService(SoporteJdbcDao dao, TiveJdbcDao tiveDao, OperacionVehiculoDao operaciones,
            AlmacenamientoDocumentos almacenamiento, ObjectMapper json) {
        this.dao = dao;
        this.tiveDao = tiveDao;
        this.operaciones = operaciones;
        this.almacenamiento = almacenamiento;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<ContratoSoporte.VehiculoElegibleDto> vehiculosElegibles(long usuario) {
        return dao.vehiculosElegibles(usuario);
    }

    @Transactional(readOnly = true)
    public List<ContratoSoporte.SolicitudResumenDto> listar(long usuario) {
        return dao.listarPropias(usuario);
    }

    @Transactional(readOnly = true)
    public ContratoSoporte.SolicitudDetalleDto detalle(long usuario, String codigo) {
        var fila = dao.porCodigoPropia(usuario, codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        return aDetalle(fila);
    }

    @Transactional(readOnly = true)
    public ContratoSoporte.EvidenciaPayload evidenciaPropia(long usuario, String codigo) {
        var fila = dao.porCodigoPropia(usuario, codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        return leerEvidencia(fila);
    }

    @Transactional
    public ContratoSoporte.CrearResponse crearCorreccionVin(
            long usuario, UUID idOperacion, long idVehiculo, String vinPropuestoRaw, String motivoRaw,
            String contentType, String nombreOriginal, byte[] bytes) {
        ValidadorSoporte.exigirIdOperacion(idOperacion);
        String motivo = ValidadorSoporte.motivo(motivoRaw);
        ValidadorSoporte.validarEvidenciaOpcional(contentType, bytes);
        boolean conEvidencia = bytes != null && bytes.length > 0;

        var ctx = dao.vehiculoPropio(usuario, idVehiculo)
                .orElseThrow(() -> new ErrorSoporte("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado."));
        if (!ctx.tieneTive()) {
            throw new ErrorSoporte("TIVE_REQUERIDA",
                    "Debes registrar la TIVE del vehículo antes de solicitar una corrección de VIN.",
                    java.util.Set.of("idVehiculo"));
        }
        if (ctx.numeroVin() == null || ctx.numeroVin().isBlank()) {
            throw new ErrorSoporte("VIN_AUSENTE", "El vehículo no tiene VIN registrado.");
        }
        String vinReg = ctx.numeroVin().strip().toUpperCase(Locale.ROOT);
        String vinProp = ValidadorSoporte.normalizarVinPropuesto(vinPropuestoRaw, vinReg);
        if (dao.vinUsadoPorOtro(vinProp, idVehiculo)) {
            throw new ErrorSoporte("VIN_DUPLICADO", "El VIN propuesto ya está en otro vehículo.",
                    java.util.Set.of("vinPropuesto"));
        }

        var huellaMap = new java.util.LinkedHashMap<String, Object>();
        huellaMap.put("contrato", "SOLICITAR_CORRECCION_VIN_V1");
        huellaMap.put("idVehiculo", idVehiculo);
        huellaMap.put("vinPropuesto", vinProp);
        huellaMap.put("motivo", motivo);
        huellaMap.put("tamano", conEvidencia ? bytes.length : 0);
        huellaMap.put("conEvidencia", conEvidencia);
        String huella = hash(huellaMap);
        boolean nueva = operaciones.reservar(idOperacion, usuario, OP, huella);
        var op = operaciones.buscarParaActualizar(idOperacion, usuario, OP)
                .orElseThrow(() -> new ErrorSoporte("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (!Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorSoporte("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerCreacion(op.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorSoporte("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }

        dao.bloquearVehiculo(idVehiculo);
        if (dao.existeAbierta(idVehiculo, TIPO)) {
            String codigo = dao.codigoAbierta(idVehiculo, TIPO).orElse("?");
            throw new ErrorSoporte("SOLICITUD_ABIERTA",
                    "Ya existe una solicitud abierta para este vehículo: " + codigo,
                    java.util.Set.of("idVehiculo"));
        }

        String key = null;
        String ct = null;
        Integer tamano = null;
        String nombre = null;
        if (conEvidencia) {
            nombre = RutasDocumentoSoporte.nombreSeguro(nombreOriginal);
            key = RutasDocumentoSoporte.claveTemp(usuario, idVehiculo, nombre);
            ct = contentType;
            tamano = bytes.length;
            try {
                almacenamiento.guardar(key, bytes, contentType);
                if (!almacenamiento.confirmarExistencia(key)) {
                    throw new ErrorSoporte("DOCUMENTO_NO_ENCONTRADO", "No se pudo confirmar la evidencia.");
                }
            } catch (ErrorAlmacenamiento ex) {
                throw new ErrorSoporte(ex.codigo(), ex.getMessage());
            }
        }

        String codigo = nuevoCodigo();
        try {
            long id = dao.insertarSolicitud(codigo, TIPO, usuario, idVehiculo);
            dao.insertarCorreccionVin(id, vinReg, vinProp, motivo, key, ct, tamano,
                    nombreOriginal == null || nombreOriginal.isBlank() ? nombre : nombreOriginal);
            dao.insertarEvento(id, null, "PENDIENTE", usuario, "Solicitud creada por el titular");
            var resp = new ContratoSoporte.CrearResponse(idOperacion, codigo, "PENDIENTE");
            confirmar(idOperacion, usuario, huella, resp);
            return resp;
        } catch (DataIntegrityViolationException ex) {
            if (key != null) {
                try { almacenamiento.borrarLogicoHuerfano(key); } catch (Exception ignored) { }
            }
            throw new ErrorSoporte("SOLICITUD_ABIERTA", "Ya existe una solicitud abierta para este vehículo.");
        }
    }

    @Transactional(readOnly = true)
    public List<ContratoSoporte.SolicitudResumenDto> listarAdmin(String estado, String q) {
        return dao.listarAdmin(estado, q);
    }

    @Transactional(readOnly = true)
    public ContratoSoporte.SolicitudDetalleDto detalleAdmin(String codigo) {
        var fila = dao.porCodigo(codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        return aDetalle(fila);
    }

    @Transactional(readOnly = true)
    public ContratoSoporte.EvidenciaPayload evidenciaAdmin(String codigo) {
        var fila = dao.porCodigo(codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        return leerEvidencia(fila);
    }

    @Transactional
    public ContratoSoporte.AccionResponse iniciarRevision(long adminId, String codigo) {
        var fila = dao.bloquearPorCodigo(codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        if (!"PENDIENTE".equals(fila.estado())) {
            throw new ErrorSoporte("ESTADO_INVALIDO", "Solo se puede iniciar revisión desde PENDIENTE.");
        }
        if (dao.actualizarEstado(fila.idSolicitud(), "EN_REVISION", null, fila.versionFila()) != 1) {
            throw new ErrorSoporte("CONFLICTO_VERSION", "El caso cambió. Recarga.");
        }
        dao.insertarEvento(fila.idSolicitud(), "PENDIENTE", "EN_REVISION", adminId, "Revisión iniciada");
        return new ContratoSoporte.AccionResponse(codigo, "EN_REVISION", fila.versionFila() + 1);
    }

    @Transactional
    public ContratoSoporte.AccionResponse rechazar(long adminId, String codigo, String motivoRaw) {
        String motivo = ValidadorSoporte.motivo(motivoRaw);
        var fila = dao.bloquearPorCodigo(codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        if ("APROBADA".equals(fila.estado()) || "RECHAZADA".equals(fila.estado())) {
            throw new ErrorSoporte("CASO_CERRADO", "El caso ya está cerrado.");
        }
        if (!"PENDIENTE".equals(fila.estado()) && !"EN_REVISION".equals(fila.estado())) {
            throw new ErrorSoporte("ESTADO_INVALIDO", "Estado no permite rechazo.");
        }
        String ant = fila.estado();
        if (dao.actualizarEstado(fila.idSolicitud(), "RECHAZADA", motivo, fila.versionFila()) != 1) {
            throw new ErrorSoporte("CONFLICTO_VERSION", "El caso cambió. Recarga.");
        }
        dao.insertarEvento(fila.idSolicitud(), ant, "RECHAZADA", adminId, motivo);
        return new ContratoSoporte.AccionResponse(codigo, "RECHAZADA", fila.versionFila() + 1);
    }

    @Transactional
    public ContratoSoporte.AccionResponse aprobar(long adminId, String codigo) {
        var fila = dao.bloquearPorCodigo(codigo)
                .orElseThrow(() -> new ErrorSoporte("SOLICITUD_NO_ENCONTRADA", "Solicitud no encontrada."));
        if ("APROBADA".equals(fila.estado()) || "RECHAZADA".equals(fila.estado())) {
            throw new ErrorSoporte("CASO_CERRADO", "El caso ya está cerrado.");
        }
        if (!"EN_REVISION".equals(fila.estado())) {
            throw new ErrorSoporte("ESTADO_INVALIDO", "Debes iniciar revisión antes de aprobar.");
        }
        dao.bloquearVehiculo(fila.idVehiculo());
        var veh = tiveDao.vehiculoPropio(fila.idUsuario(), fila.idVehiculo())
                .orElseThrow(() -> new ErrorSoporte("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado."));
        String vinActual = veh.numeroVin() == null ? "" : veh.numeroVin().strip().toUpperCase(Locale.ROOT);
        if (!vinActual.equals(fila.vinRegistrado())) {
            throw new ErrorSoporte("VIN_ACTUAL_CAMBIO",
                    "El VIN del vehículo ya no coincide con el de la solicitud.");
        }
        if (!tiveDao.existeParaVehiculo(fila.idVehiculo())) {
            throw new ErrorSoporte("TIVE_INCONSISTENTE", "No hay TIVE asociada al vehículo.");
        }
        if (dao.vinUsadoPorOtro(fila.vinPropuesto(), fila.idVehiculo())) {
            throw new ErrorSoporte("VIN_DUPLICADO", "El VIN propuesto ya está en uso.");
        }
        int sync = tiveDao.actualizarVinVehiculoYTive(fila.idVehiculo(), fila.vinPropuesto());
        if (sync < 2) {
            throw new ErrorSoporte("APROBACION_FALLIDA", "No se pudo sincronizar el VIN.");
        }
        if (dao.actualizarEstado(fila.idSolicitud(), "APROBADA", null, fila.versionFila()) != 1) {
            throw new ErrorSoporte("CONFLICTO_VERSION", "El caso cambió. Recarga.");
        }
        dao.insertarEvento(fila.idSolicitud(), "EN_REVISION", "APROBADA", adminId,
                "VIN actualizado a " + fila.vinPropuesto());
        return new ContratoSoporte.AccionResponse(codigo, "APROBADA", fila.versionFila() + 1);
    }

    private ContratoSoporte.SolicitudDetalleDto aDetalle(SoporteJdbcDao.FilaSolicitud fila) {
        return new ContratoSoporte.SolicitudDetalleDto(
                fila.codigo(), fila.tipo(), fila.estado(), fila.idVehiculo(), fila.placa(),
                fila.marca(), fila.modelo(), fila.nombreUsuario(), fila.correoUsuario(),
                fila.vinRegistrado(), fila.vinPropuesto(), fila.motivo(), fila.motivoRechazo(),
                fila.storageObjectKey() != null, fila.contentType(), fila.nombreOriginal(),
                fila.tamanoBytes(), fila.versionFila(),
                fila.creadoUtc(), fila.actualizadoUtc(), dao.eventos(fila.idSolicitud()));
    }

    private ContratoSoporte.EvidenciaPayload leerEvidencia(SoporteJdbcDao.FilaSolicitud fila) {
        if (fila.storageObjectKey() == null) {
            throw new ErrorSoporte("DOCUMENTO_NO_DISPONIBLE", "La evidencia no está disponible.");
        }
        try {
            byte[] bytes = almacenamiento.leer(fila.storageObjectKey());
            String ct = fila.contentType() == null || fila.contentType().isBlank()
                    ? "application/octet-stream"
                    : fila.contentType();
            String nombre = fila.nombreOriginal() == null || fila.nombreOriginal().isBlank()
                    ? "evidencia"
                    : fila.nombreOriginal();
            return new ContratoSoporte.EvidenciaPayload(bytes, ct, nombre);
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorSoporte(ex.codigo(), ex.getMessage());
        }
    }

    private String nuevoCodigo() {
        String dia = LocalDate.now(ZoneId.of("America/Lima")).format(DateTimeFormatter.BASIC_ISO_DATE);
        int n = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "VIN-" + dia + "-" + n;
    }

    private void confirmar(UUID idOperacion, long usuario, String huella, Object resp) {
        try {
            String payload = json.writeValueAsString(resp);
            if (!operaciones.confirmar(idOperacion, usuario, OP, huella, payload)) {
                throw new ErrorSoporte("OPERACION_EN_PROCESO", "No se pudo confirmar.", true);
            }
        } catch (ErrorSoporte ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ContratoSoporte.CrearResponse leerCreacion(String payload) {
        try {
            return json.readValue(payload, ContratoSoporte.CrearResponse.class);
        } catch (Exception ex) {
            throw new ErrorSoporte("OPERACION_EN_PROCESO", "Resultado pendiente.", true);
        }
    }

    private String hash(Object body) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(body));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
