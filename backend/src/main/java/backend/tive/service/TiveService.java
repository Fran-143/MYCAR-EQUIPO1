package backend.tive.service;

import backend.documentos.AlmacenamientoDocumentos;
import backend.documentos.ErrorAlmacenamiento;
import backend.documentos.RutasDocumentoTive;
import backend.tive.api.dto.ContratoTive;
import backend.tive.dao.TiveJdbcDao;
import backend.tive.model.ErrorTive;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TiveService {
    private static final String TIPO_REGISTRAR = "REGISTRAR_TIVE";

    private final TiveJdbcDao dao;
    private final OperacionVehiculoDao operaciones;
    private final AlmacenamientoDocumentos almacenamiento;
    private final ObjectMapper json;

    public TiveService(TiveJdbcDao dao, OperacionVehiculoDao operaciones,
            AlmacenamientoDocumentos almacenamiento, ObjectMapper json) {
        this.dao = dao;
        this.operaciones = operaciones;
        this.almacenamiento = almacenamiento;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ContratoTive.PrecheckResponse precheck(long usuario, long vehiculo) {
        var ctx = dao.vehiculoPropio(usuario, vehiculo)
                .orElseThrow(() -> new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado."));
        boolean tieneTive = dao.existeParaVehiculo(vehiculo);
        boolean tieneVin = ctx.numeroVin() != null && !ctx.numeroVin().isBlank();
        if (!"ACTIVO".equals(ctx.estado())) {
            return new ContratoTive.PrecheckResponse(false, tieneVin, tieneTive,
                    ctx.placa(), ctx.numeroVin(), ctx.numeroMotor(), ctx.numeroSerie(),
                    "VEHICULO_INACTIVO", "Solo puedes registrar TIVE en un vehículo activo.");
        }
        if (tieneTive) {
            return new ContratoTive.PrecheckResponse(false, tieneVin, true,
                    ctx.placa(), ctx.numeroVin(), ctx.numeroMotor(), ctx.numeroSerie(),
                    "TIVE_EXISTE", "Este vehículo ya tiene una TIVE registrada.");
        }
        if (!tieneVin) {
            return new ContratoTive.PrecheckResponse(false, false, false,
                    ctx.placa(), null, ctx.numeroMotor(), ctx.numeroSerie(),
                    "VIN_AUSENTE", "Completa el VIN del vehículo antes de registrar la TIVE.");
        }
        try {
            ValidadorTive.normalizarVin(ctx.numeroVin());
        } catch (ErrorTive ex) {
            return new ContratoTive.PrecheckResponse(false, true, false,
                    ctx.placa(), ctx.numeroVin(), ctx.numeroMotor(), ctx.numeroSerie(),
                    ex.codigo(), ex.getMessage());
        }
        try {
            ValidadorTive.normalizarPlaca(ctx.placa());
        } catch (ErrorTive ex) {
            return new ContratoTive.PrecheckResponse(false, true, false,
                    ctx.placa(), ctx.numeroVin(), ctx.numeroMotor(), ctx.numeroSerie(),
                    "DATOS_INVALIDOS", ex.getMessage());
        }
        return new ContratoTive.PrecheckResponse(true, true, false,
                ctx.placa(), ctx.numeroVin(), ctx.numeroMotor(), ctx.numeroSerie(),
                null, null);
    }

    @Transactional(readOnly = true)
    public ContratoTive.ConsultaResponse consultar(long usuario, long vehiculo) {
        var ctx = dao.vehiculoPropio(usuario, vehiculo)
                .orElseThrow(() -> new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado."));
        var filaOpt = dao.obtenerPorVehiculo(vehiculo);
        if (filaOpt.isEmpty()) {
            return new ContratoTive.ConsultaResponse(
                    null,
                    "No hay TIVE registrada para este vehículo.",
                    ctx.marca(), ctx.modelo(), ctx.placa(), ctx.numeroVin(),
                    ctx.anioFabricacion(), ctx.color());
        }
        var fila = filaOpt.get();
        return new ContratoTive.ConsultaResponse(
                dto(fila),
                fila.storageObjectKey() == null ? "Sin documento adjunto" : null,
                ctx.marca(), ctx.modelo(), ctx.placa(), ctx.numeroVin(),
                ctx.anioFabricacion(), ctx.color());
    }

    @Transactional
    public ContratoTive.RegistrarResponse registrar(long usuario, long vehiculo, ContratoTive.RegistrarRequest req) {
        ValidadorTive.exigirRegistro(req);
        String motor = ValidadorTive.normalizarMotor(req.numeroMotor());
        String serie = ValidadorTive.normalizarSerie(req.numeroSerie());
        String titulo = req.numeroTitulo().strip().toUpperCase(Locale.ROOT);
        String codigo = req.codigoVerificacion().strip().toUpperCase(Locale.ROOT);
        String zona = blancoNullUpper(req.zonaRegistral());
        String sede = blancoNullUpper(req.sedeRegistral());

        String huella = HuellaOperacionTive.registrar(vehiculo, req);
        boolean nueva;
        try {
            nueva = operaciones.reservar(req.idOperacion(), usuario, TIPO_REGISTRAR, huella);
        } catch (DataIntegrityViolationException ex) {
            throw IntegridadTive.mapear(ex);
        }
        var op = operaciones.buscarParaActualizar(req.idOperacion(), usuario, TIPO_REGISTRAR)
                .orElseThrow(() -> new ErrorTive("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (!Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorTive("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerRegistroConfirmado(op.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorTive("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }

        try {
            dao.exigirVehiculoActivoBloqueado(usuario, vehiculo);
        } catch (Exception ex) {
            var ctx = dao.vehiculoPropio(usuario, vehiculo).orElse(null);
            if (ctx == null) throw new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
            if ("INACTIVO".equals(ctx.estado())) {
                throw new ErrorTive("VEHICULO_INACTIVO", "Esta operación solo está disponible para vehículos activos.");
            }
            throw new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
        dao.bloquearTiveVehiculo(vehiculo);
        if (dao.existeParaVehiculo(vehiculo)) {
            throw new ErrorTive("TIVE_EXISTE", "Este vehículo ya tiene una TIVE registrada.");
        }
        var ctx = dao.vehiculoPropio(usuario, vehiculo)
                .orElseThrow(() -> new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado."));
        String vin = ValidadorTive.normalizarVin(ctx.numeroVin());
        String placa = ValidadorTive.normalizarPlaca(ctx.placa());
        if (dao.vinUsadoPorOtro(vin, vehiculo)) {
            throw new ErrorTive("VIN_DUPLICADO", "Este VIN ya está registrado en otro vehículo.",
                    java.util.Set.of("numeroVin"));
        }
        var doc = resolverDocumentoNuevo(usuario, vehiculo, req.documento());
        try {
            long idTive = dao.insertar(new TiveJdbcDao.DatosInsercion(
                    vehiculo, titulo, codigo, req.fechaTitulo(), zona, sede,
                    vin, placa, motor, serie,
                    doc == null ? null : doc.objectKey(),
                    doc == null ? null : doc.contentType(),
                    doc == null ? null : doc.tamanoBytes(),
                    doc == null ? null : doc.nombreOriginal()));
            dao.sincronizarMotorSerieVehiculo(vehiculo, motor, serie);
            var resp = new ContratoTive.RegistrarResponse(req.idOperacion(), idTive);
            confirmarOperacion(req.idOperacion(), usuario, TIPO_REGISTRAR, huella, resp);
            return resp;
        } catch (DataIntegrityViolationException ex) {
            throw IntegridadTive.mapear(ex);
        } catch (ErrorTive ex) {
            throw ex;
        } catch (DataAccessException ex) {
            // PSQLException de triggers (p.ej. motor eléctrico) no siempre llega como IntegrityViolation.
            String detalle = ex.getMostSpecificCause() != null
                    ? String.valueOf(ex.getMostSpecificCause().getMessage())
                    : String.valueOf(ex.getMessage());
            if (detalle != null && detalle.toLowerCase(Locale.ROOT).contains("motor eléctrico")) {
                throw new ErrorTive(
                        "DATOS_INVALIDOS",
                        "En vehículos eléctricos el número de motor no se guarda en la ficha del vehículo.");
            }
            throw new ErrorTive(
                    "TIVE_GUARDAR_FALLIDO",
                    "No pudimos guardar la TIVE por un problema interno. Inténtalo de nuevo.");
        }
    }

    @Transactional
    public ContratoTive.ActualizarResponse actualizar(long usuario, long vehiculo, long idTive,
            ContratoTive.ActualizarRequest req) {
        ValidadorTive.exigirActualizar(req);
        String motor = ValidadorTive.normalizarMotor(req.numeroMotor());
        String serie = ValidadorTive.normalizarSerie(req.numeroSerie());
        String titulo = req.numeroTitulo().strip().toUpperCase(Locale.ROOT);
        String codigo = req.codigoVerificacion().strip().toUpperCase(Locale.ROOT);
        String zona = blancoNullUpper(req.zonaRegistral());
        String sede = blancoNullUpper(req.sedeRegistral());

        try {
            dao.exigirVehiculoActivoBloqueado(usuario, vehiculo);
        } catch (Exception ex) {
            var ctx = dao.vehiculoPropio(usuario, vehiculo).orElse(null);
            if (ctx == null) throw new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
            if ("INACTIVO".equals(ctx.estado())) {
                throw new ErrorTive("VEHICULO_INACTIVO", "Esta operación solo está disponible para vehículos activos.");
            }
            throw new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no disponible.");
        }
        dao.bloquearTiveVehiculo(vehiculo);
        var actual = dao.obtenerPropio(usuario, vehiculo, idTive)
                .orElseThrow(() -> new ErrorTive("TIVE_NO_ENCONTRADA", "TIVE no encontrada."));
        DocumentoResuelto doc = resolverDocumentoActualizar(usuario, vehiculo, actual, req);
        if (sinCambios(actual, req, titulo, codigo, zona, sede, motor, serie, doc)) {
            throw new ErrorTive("SIN_CAMBIOS", "No hay cambios para guardar.");
        }
        String antes = serializar(actual);
        int filas = dao.actualizar(new TiveJdbcDao.DatosActualizacion(
                idTive, vehiculo, actual.versionFila(),
                titulo, codigo, req.fechaTitulo(), zona, sede, motor, serie,
                doc.objectKey(), doc.contentType(), doc.tamanoBytes(), doc.nombreOriginal()));
        if (filas != 1) throw new ErrorTive("CONFLICTO_VERSION", "La TIVE cambió. Vuelve a cargar.");
        dao.sincronizarMotorSerieVehiculo(vehiculo, motor, serie);
        limpiarDocumentoAnteriorSiCorresponde(actual.storageObjectKey(), doc.objectKey(), req.accionDocumento());
        var actualizado = dao.obtenerPropio(usuario, vehiculo, idTive).orElseThrow();
        dao.insertarCambio(idTive, usuario, antes, serializar(actualizado));
        return new ContratoTive.ActualizarResponse(idTive, actualizado.versionFila());
    }

    @Transactional(readOnly = true)
    public TiveJdbcDao.FilaTive obtenerFilaDocumento(long usuario, long vehiculo, long idTive) {
        return dao.obtenerPropio(usuario, vehiculo, idTive)
                .orElseThrow(() -> new ErrorTive("TIVE_NO_ENCONTRADA", "TIVE no encontrada."));
    }

    @Transactional(readOnly = true)
    public byte[] leerDocumentoBytes(long usuario, long vehiculo, long idTive) {
        var fila = obtenerFilaDocumento(usuario, vehiculo, idTive);
        if (fila.storageObjectKey() == null) {
            throw new ErrorTive("DOCUMENTO_NO_DISPONIBLE", "Esta TIVE no tiene documento adjunto.");
        }
        if (!RutasDocumentoTive.perteneceAVehiculo(fila.storageObjectKey(), usuario, vehiculo)) {
            throw new ErrorTive("DOCUMENTO_NO_AUTORIZADO", "No autorizado.");
        }
        try {
            return almacenamiento.leer(fila.storageObjectKey());
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorTive(ex.codigo(), ex.getMessage());
        }
    }

    @Transactional
    public ContratoTive.DocumentoDto subirDocumentoAutenticado(
            long usuario, long vehiculo, String contentType, String nombreOriginal, byte[] bytes) {
        exigirVehiculoActivoMutacion(usuario, vehiculo);
        ValidadorTive.exigirMimeYTamano(contentType, bytes == null ? 0 : bytes.length);
        String nombre = RutasDocumentoTive.nombreSeguro(nombreOriginal);
        String key = RutasDocumentoTive.claveTemp(usuario, vehiculo, nombre);
        try {
            almacenamiento.guardar(key, bytes, contentType);
            return new ContratoTive.DocumentoDto(key, contentType, bytes.length, nombreOriginal);
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorTive(ex.codigo(), ex.getMessage());
        }
    }

    private void exigirTitularidadConsulta(long usuario, long vehiculo) {
        if (vehiculo <= 0) throw new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        if (dao.vehiculoPropio(usuario, vehiculo).isEmpty()) {
            throw new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado.");
        }
    }

    private void exigirVehiculoActivoMutacion(long usuario, long vehiculo) {
        var ctx = dao.vehiculoPropio(usuario, vehiculo)
                .orElseThrow(() -> new ErrorTive("VEHICULO_NO_ENCONTRADO", "Vehículo no encontrado."));
        if (!"ACTIVO".equals(ctx.estado())) {
            throw new ErrorTive("VEHICULO_INACTIVO", "Esta operación solo está disponible para vehículos activos.");
        }
    }

    private ContratoTive.DocumentoDto resolverDocumentoNuevo(
            long usuario, long vehiculo, ContratoTive.DocumentoDto doc) {
        if (doc == null) return null;
        if (!RutasDocumentoTive.perteneceAVehiculo(doc.objectKey(), usuario, vehiculo)) {
            throw new ErrorTive("DOCUMENTO_NO_AUTORIZADO", "La ruta del documento no es válida.");
        }
        try {
            if (!almacenamiento.confirmarExistencia(doc.objectKey())) {
                throw new ErrorTive("DOCUMENTO_NO_ENCONTRADO", "Sube el documento antes de confirmar.");
            }
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorTive(ex.codigo(), ex.getMessage());
        }
        return doc;
    }

    private DocumentoResuelto resolverDocumentoActualizar(
            long usuario, long vehiculo, TiveJdbcDao.FilaTive actual, ContratoTive.ActualizarRequest req) {
        return switch (req.accionDocumento()) {
            case MANTENER -> new DocumentoResuelto(
                    actual.storageObjectKey(), actual.contentType(), actual.tamanoBytes(), actual.nombreOriginal());
            case QUITAR -> new DocumentoResuelto(null, null, null, null);
            case AGREGAR, REEMPLAZAR -> {
                ContratoTive.DocumentoDto nuevo = resolverDocumentoNuevo(usuario, vehiculo, req.documento());
                if (nuevo == null) yield new DocumentoResuelto(null, null, null, null);
                yield new DocumentoResuelto(
                        nuevo.objectKey(), nuevo.contentType(), nuevo.tamanoBytes(), nuevo.nombreOriginal());
            }
        };
    }

    private void limpiarDocumentoAnteriorSiCorresponde(
            String claveAnterior, String claveNueva, ContratoTive.AccionDocumento accion) {
        if (claveAnterior == null || claveAnterior.isBlank()) return;
        if (accion == ContratoTive.AccionDocumento.MANTENER) return;
        if ((accion == ContratoTive.AccionDocumento.REEMPLAZAR || accion == ContratoTive.AccionDocumento.AGREGAR)
                && Objects.equals(claveAnterior, claveNueva)) {
            return;
        }
        try {
            almacenamiento.borrarLogicoHuerfano(claveAnterior);
        } catch (ErrorAlmacenamiento ignored) {
            // La fila ya apunta al nuevo estado.
        }
    }

    private boolean sinCambios(TiveJdbcDao.FilaTive actual, ContratoTive.ActualizarRequest req,
            String titulo, String codigo, String zona, String sede, String motor, String serie,
            DocumentoResuelto doc) {
        boolean mismos = Objects.equals(actual.numeroTitulo(), titulo)
                && Objects.equals(actual.codigoVerificacion(), codigo)
                && Objects.equals(actual.fechaTitulo(), req.fechaTitulo())
                && Objects.equals(actual.zonaRegistral(), zona)
                && Objects.equals(actual.sedeRegistral(), sede)
                && Objects.equals(actual.numeroMotor(), motor)
                && Objects.equals(actual.numeroSerie(), serie)
                && Objects.equals(actual.storageObjectKey(), doc.objectKey());
        return mismos;
    }

    private void confirmarOperacion(UUID idOperacion, long usuario, String tipo, String huella, Object respuesta) {
        try {
            String payload = json.writeValueAsString(respuesta);
            if (!operaciones.confirmar(idOperacion, usuario, tipo, huella, payload)) {
                throw new ErrorTive("OPERACION_EN_PROCESO", "No se pudo confirmar la operación.", true);
            }
        } catch (ErrorTive ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar respuesta TIVE", ex);
        }
    }

    private ContratoTive.RegistrarResponse leerRegistroConfirmado(String payload) {
        try {
            return json.readValue(payload, ContratoTive.RegistrarResponse.class);
        } catch (Exception ex) {
            throw new ErrorTive("OPERACION_EN_PROCESO", "Resultado pendiente.", true);
        }
    }

    private String serializar(TiveJdbcDao.FilaTive fila) {
        try {
            var snap = new LinkedHashMap<String, Object>();
            snap.put("idTive", fila.idTive());
            snap.put("numeroTitulo", fila.numeroTitulo());
            snap.put("codigoVerificacion", fila.codigoVerificacion());
            snap.put("fechaTitulo", fila.fechaTitulo());
            snap.put("zonaRegistral", fila.zonaRegistral());
            snap.put("sedeRegistral", fila.sedeRegistral());
            snap.put("numeroVin", fila.numeroVin());
            snap.put("placa", fila.placa());
            snap.put("numeroMotor", fila.numeroMotor());
            snap.put("numeroSerie", fila.numeroSerie());
            snap.put("tieneDocumento", fila.storageObjectKey() != null);
            snap.put("versionFila", fila.versionFila());
            return json.writeValueAsString(snap);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar TIVE", ex);
        }
    }

    private static ContratoTive.TiveDto dto(TiveJdbcDao.FilaTive f) {
        ContratoTive.DocumentoDto doc = f.storageObjectKey() == null ? null
                : new ContratoTive.DocumentoDto(f.storageObjectKey(), f.contentType(), f.tamanoBytes(), f.nombreOriginal());
        return new ContratoTive.TiveDto(
                f.idTive(), f.numeroTitulo(), f.codigoVerificacion(), f.fechaTitulo(),
                f.zonaRegistral(), f.sedeRegistral(), f.numeroVin(), f.placa(),
                f.numeroMotor(), f.numeroSerie(), f.storageObjectKey() != null, f.versionFila(), doc);
    }

    private static String blancoNullUpper(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.strip().toUpperCase(Locale.ROOT);
    }

    private record DocumentoResuelto(
            String objectKey, String contentType, Integer tamanoBytes, String nombreOriginal) { }
}
