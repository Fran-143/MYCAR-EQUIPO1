package backend.licencia.service;

import backend.crypto.DatosSensiblesCryptoGate;
import backend.documentos.AlmacenamientoDocumentos;
import backend.documentos.ErrorAlmacenamiento;
import backend.documentos.RutasDocumentoLicencia;
import backend.licencia.api.dto.ContratoLicencia;
import backend.licencia.dao.LicenciaJdbcDao;
import backend.licencia.model.ErrorLicencia;
import backend.notificaciones.service.NotificacionInternaService;
import backend.soat.model.ErrorSoat;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LicenciaService {
    private static final String TIPO_REGISTRAR = "REGISTRAR_LICENCIA";
    private static final String TIPO_RENOVAR = "RENOVAR_LICENCIA";

    private final LicenciaJdbcDao dao;
    private final OperacionVehiculoDao operaciones;
    private final AlmacenamientoDocumentos almacenamiento;
    private final DatosSensiblesCryptoGate cryptoGate;
    private final ObjectMapper json;
    private final ObjectProvider<NotificacionInternaService> notificaciones;

    public LicenciaService(
            LicenciaJdbcDao dao,
            OperacionVehiculoDao operaciones,
            AlmacenamientoDocumentos almacenamiento,
            DatosSensiblesCryptoGate cryptoGate,
            ObjectMapper json,
            ObjectProvider<NotificacionInternaService> notificaciones) {
        this.dao = dao;
        this.operaciones = operaciones;
        this.almacenamiento = almacenamiento;
        this.cryptoGate = cryptoGate;
        this.json = json;
        this.notificaciones = notificaciones;
    }

    @Transactional(readOnly = true)
    public ContratoLicencia.ConsultaResponse consultar(long usuario) {
        LocalDate hoy = dao.hoyLima();
        var actualOpt = dao.obtenerActual(usuario).map(f -> dtoPublico(f, hoy));
        var historialFilas = dao.listarHistorial(usuario);
        boolean tieneHistorial = !historialFilas.isEmpty();
        var historial = historialFilas.stream()
                .map(f -> LicenciaJdbcDao.aHistorialResumen(f, abrirNumero(f)))
                .toList();
        ContratoLicencia.RecordatorioDto recordatorio = null;
        if (actualOpt.isPresent()) {
            var alerta = dao.alertaActivaPorLicencia(actualOpt.get().idLicencia()).orElse(null);
            recordatorio = new ContratoLicencia.RecordatorioDto(
                    actualOpt.get().recordatorioActivo(),
                    alerta == null ? null : alerta.idAlerta(),
                    alerta == null ? null : alerta.estado());
        }
        return new ContratoLicencia.ConsultaResponse(actualOpt.orElse(null), tieneHistorial, historial, recordatorio);
    }

    @Transactional
    public ContratoLicencia.RegistrarResponse registrar(long usuario, ContratoLicencia.RegistrarRequest req) {
        ValidadorLicencia.exigirRegistro(req);
        String huella = HuellaOperacionLicencia.registrar(usuario, req);
        boolean nueva = operaciones.reservar(req.idOperacion(), usuario, TIPO_REGISTRAR, huella);
        var op = operaciones.buscarParaActualizar(req.idOperacion(), usuario, TIPO_REGISTRAR)
                .orElseThrow(() -> new ErrorLicencia("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (!Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorLicencia("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerRegistroConfirmado(op.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorLicencia("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }
        dao.bloquearLicenciaUsuario(usuario);
        if (dao.existeActual(usuario)) {
            throw new ErrorLicencia("LICENCIA_ACTUAL_EXISTE", "Ya tienes una licencia registrada.");
        }
        String blind = blindDeNumero(req.numero());
        if (dao.numeroEnUso(usuario, blind, null)) {
            throw new ErrorLicencia("LICENCIA_NUMERO_DUPLICADO",
                    "Este número ya está registrado en tu historial o licencia actual.", Set.of("numero"));
        }
        if (dao.existeDuplicadoExacto(usuario, blind, req.categoria(),
                req.fechaEmision(), req.fechaVencimiento(), null)) {
            throw new ErrorLicencia("LICENCIA_DUPLICADA",
                    ValidadorLicencia.MSG_LICENCIA_DUPLICADA,
                    Set.of("numero", "fechaEmision", "fechaVencimiento"));
        }
        FotoResuelta frontal = resolverFotoNueva(usuario, null, req.fotoFrontal(), "FRONTAL");
        FotoResuelta posterior = resolverFotoNueva(usuario, null, req.fotoPosterior(), "POSTERIOR");
        var sensibles = sellarNumero(usuario, null, req.numero());
        try {
            long id = dao.insertar(new LicenciaJdbcDao.DatosInsercion(
                    usuario, req.categoria(), sensibles.numeroCifrado(), sensibles.blind(), sensibles.keyId(),
                    req.fechaEmision(), req.fechaVencimiento(), "ACTUAL", false,
                    frontal.objectKey(), frontal.contentType(), frontal.tamanoBytes(), frontal.nombreOriginal(),
                    posterior.objectKey(), posterior.contentType(), posterior.tamanoBytes(), posterior.nombreOriginal(),
                    frontal.cifrado() || posterior.cifrado()));
            LocalDate hoy = dao.hoyLima();
            var resp = new ContratoLicencia.RegistrarResponse(
                    req.idOperacion(), id, ValidadorLicencia.estadoVigencia(req.fechaVencimiento(), hoy));
            confirmarOperacion(req.idOperacion(), usuario, TIPO_REGISTRAR, huella, resp);
            return resp;
        } catch (DataIntegrityViolationException ex) {
            throw new ErrorLicencia("LICENCIA_ACTUAL_EXISTE", "Ya tienes una licencia registrada.");
        } catch (ErrorLicencia ex) {
            throw ex;
        } catch (ErrorSoat ex) {
            throw new ErrorLicencia(ex.codigo(), ex.getMessage());
        } catch (DataAccessException ex) {
            throw new ErrorLicencia("LICENCIA_GUARDAR_FALLIDO", "No pudimos guardar la licencia. Inténtalo de nuevo");
        }
    }

    @Transactional
    public ContratoLicencia.EditarResponse editar(long usuario, ContratoLicencia.EditarRequest req) {
        ValidadorLicencia.exigirEditar(req);
        dao.bloquearLicenciaUsuario(usuario);
        var actual = dao.obtenerActual(usuario)
                .orElseThrow(() -> new ErrorLicencia("LICENCIA_NO_ENCONTRADA", "No tienes licencia registrada."));
        String blind = blindDeNumero(req.numero());
        if (dao.numeroEnUso(usuario, blind, actual.idLicencia())) {
            throw new ErrorLicencia("LICENCIA_NUMERO_DUPLICADO",
                    "Este número ya está registrado en otra licencia tuya.", Set.of("numero"));
        }
        if (dao.existeDuplicadoExacto(usuario, blind, req.categoria(),
                req.fechaEmision(), req.fechaVencimiento(), actual.idLicencia())) {
            throw new ErrorLicencia("LICENCIA_DUPLICADA",
                    ValidadorLicencia.MSG_LICENCIA_DUPLICADA,
                    Set.of("numero", "fechaEmision", "fechaVencimiento"));
        }
        FotoResuelta frontal = resolverFotoEditar(usuario, actual, req.accionFotoFrontal(), req.fotoFrontal(), true);
        FotoResuelta posterior = resolverFotoEditar(usuario, actual, req.accionFotoPosterior(), req.fotoPosterior(), false);
        exigirAmbasFotos(frontal, posterior);
        if (sinCambiosEditar(actual, req, frontal, posterior)) {
            throw new ErrorLicencia("SIN_CAMBIOS", "No hay cambios para guardar.");
        }
        var sensibles = sellarNumero(usuario, actual.idLicencia(), req.numero());
        int filas = dao.actualizarEditar(new LicenciaJdbcDao.DatosActualizacion(
                actual.idLicencia(), usuario, actual.versionFila(),
                req.categoria(), sensibles.numeroCifrado(), sensibles.blind(), sensibles.keyId(),
                req.fechaEmision(), req.fechaVencimiento(),
                frontal.objectKey(), frontal.contentType(), frontal.tamanoBytes(), frontal.nombreOriginal(),
                posterior.objectKey(), posterior.contentType(), posterior.tamanoBytes(), posterior.nombreOriginal(),
                frontal.cifrado() || posterior.cifrado()));
        if (filas != 1) {
            throw new ErrorLicencia("CONFLICTO_VERSION", "La licencia cambió. Vuelve a cargar.");
        }
        limpiarFotoSiCorresponde(actual.fotoFrontalKey(), frontal.objectKey(), req.accionFotoFrontal());
        limpiarFotoSiCorresponde(actual.fotoPosteriorKey(), posterior.objectKey(), req.accionFotoPosterior());
        var actualizado = dao.obtenerPropio(usuario, actual.idLicencia()).orElseThrow();
        if (actual.recordatorioActivo()) {
            reconciliarAlerta(usuario, actualizado.idLicencia(), actualizado.fechaVencimiento());
        }
        LocalDate hoy = dao.hoyLima();
        return new ContratoLicencia.EditarResponse(
                actualizado.idLicencia(), actualizado.versionFila(),
                ValidadorLicencia.estadoVigencia(actualizado.fechaVencimiento(), hoy));
    }

    @Transactional
    public ContratoLicencia.RenovarResponse renovar(long usuario, ContratoLicencia.RenovarRequest req) {
        ValidadorLicencia.exigirRenovar(req);
        String huella = HuellaOperacionLicencia.renovar(usuario, req);
        boolean nueva = operaciones.reservar(req.idOperacion(), usuario, TIPO_RENOVAR, huella);
        var op = operaciones.buscarParaActualizar(req.idOperacion(), usuario, TIPO_RENOVAR)
                .orElseThrow(() -> new ErrorLicencia("CONFLICTO_IDEMPOTENCIA", "Operación no disponible."));
        if (!Objects.equals(op.hashSolicitud(), huella)) {
            throw new ErrorLicencia("CONFLICTO_IDEMPOTENCIA", "Esta operación ya existe con otros datos.");
        }
        if ("CONFIRMADA".equals(op.estado())) {
            return leerRenovacionConfirmada(op.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(op.estado())) {
            throw new ErrorLicencia("OPERACION_EN_PROCESO", "La operación sigue en proceso.", true);
        }
        dao.bloquearLicenciaUsuario(usuario);
        var anterior = dao.obtenerPropio(usuario, req.idLicenciaAnterior())
                .filter(l -> "ACTUAL".equals(l.vigencia()))
                .orElseThrow(() -> new ErrorLicencia("LICENCIA_NO_ENCONTRADA", "Licencia anterior no encontrada."));
        LocalDate hoyRenovar = dao.hoyLima();
        ValidadorLicencia.exigirPuedeRenovar(anterior.fechaVencimiento(), hoyRenovar);
        ValidadorLicencia.exigirVencimientoRenovacion(req.fechaVencimiento(), anterior.fechaVencimiento());
        String blind = blindDeNumero(req.numero());
        String numeroAnterior = abrirNumero(anterior);
        ValidadorLicencia.exigirNoDuplicadaExacta(
                req.categoria(), req.numero(), req.fechaEmision(), req.fechaVencimiento(),
                anterior.categoria(), numeroAnterior, anterior.fechaEmision(), anterior.fechaVencimiento());
        if (dao.numeroEnUso(usuario, blind, anterior.idLicencia())) {
            throw new ErrorLicencia("LICENCIA_NUMERO_DUPLICADO",
                    "Este número ya está registrado en otra licencia tuya.", Set.of("numero"));
        }
        if (dao.existeDuplicadoExacto(usuario, blind, req.categoria(),
                req.fechaEmision(), req.fechaVencimiento(), anterior.idLicencia())) {
            throw new ErrorLicencia("LICENCIA_DUPLICADA",
                    ValidadorLicencia.MSG_LICENCIA_DUPLICADA,
                    Set.of("numero", "fechaEmision", "fechaVencimiento"));
        }
        exigirFotosRenovacionNoReutilizadas(anterior, req);
        boolean recordatorio = anterior.recordatorioActivo();
        cerrarAlertaPorRenovacion(anterior.idLicencia());
        int hist = dao.marcarHistorico(anterior.idLicencia(), usuario, anterior.versionFila());
        if (hist != 1) {
            throw new ErrorLicencia("CONFLICTO_VERSION", "No se pudo archivar la licencia anterior.");
        }
        FotoResuelta frontal = req.fotoFrontal() == null ? fotoVacia()
                : resolverFotoNueva(usuario, null, req.fotoFrontal(), "FRONTAL");
        FotoResuelta posterior = req.fotoPosterior() == null ? fotoVacia()
                : resolverFotoNueva(usuario, null, req.fotoPosterior(), "POSTERIOR");
        var sensibles = sellarNumero(usuario, null, req.numero());
        long idNuevo = dao.insertar(new LicenciaJdbcDao.DatosInsercion(
                usuario, req.categoria(), sensibles.numeroCifrado(), sensibles.blind(), sensibles.keyId(),
                req.fechaEmision(), req.fechaVencimiento(), "ACTUAL", recordatorio,
                frontal.objectKey(), frontal.contentType(), frontal.tamanoBytes(), frontal.nombreOriginal(),
                posterior.objectKey(), posterior.contentType(), posterior.tamanoBytes(), posterior.nombreOriginal(),
                frontal.cifrado() || posterior.cifrado()));
        if (recordatorio) {
            long idAlerta = activarAlerta(usuario, idNuevo, req.fechaVencimiento());
            sincronizarNotificacion(idAlerta);
        }
        LocalDate hoy = dao.hoyLima();
        var resp = new ContratoLicencia.RenovarResponse(
                req.idOperacion(), req.idLicenciaAnterior(), idNuevo,
                ValidadorLicencia.estadoVigencia(req.fechaVencimiento(), hoy));
        confirmarOperacion(req.idOperacion(), usuario, TIPO_RENOVAR, huella, resp);
        return resp;
    }

    @Transactional
    public ContratoLicencia.RecordatorioResponse configurarRecordatorio(
            long usuario, ContratoLicencia.RecordatorioRequest req) {
        if (req == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Solicitud vacía.");
        }
        dao.bloquearLicenciaUsuario(usuario);
        var fila = dao.obtenerActual(usuario)
                .orElseThrow(() -> new ErrorLicencia("LICENCIA_NO_ENCONTRADA", "No tienes licencia registrada."));
        if (req.activo() == fila.recordatorioActivo()) {
            var alerta = dao.alertaActivaPorLicencia(fila.idLicencia()).orElse(null);
            return new ContratoLicencia.RecordatorioResponse(
                    fila.idLicencia(), fila.recordatorioActivo(),
                    alerta == null ? null : alerta.idAlerta(),
                    alerta == null ? null : alerta.estado());
        }
        int filas = dao.actualizarRecordatorio(fila.idLicencia(), usuario, req.activo(), fila.versionFila());
        if (filas != 1) {
            throw new ErrorLicencia("CONFLICTO_VERSION", "La licencia cambió. Vuelve a cargar.");
        }
        Long idAlerta = null;
        String estadoAlerta = null;
        if (req.activo()) {
            idAlerta = activarAlerta(usuario, fila.idLicencia(), fila.fechaVencimiento());
            estadoAlerta = dao.alertaActivaPorLicencia(fila.idLicencia()).map(LicenciaJdbcDao.AlertaLicencia::estado).orElse(null);
            if (!"VENCIDA".equals(estadoAlerta)) {
                sincronizarNotificacion(idAlerta);
            }
        } else {
            Long idAnulada = desactivarAlertaSiActiva(fila.idLicencia());
            if (idAnulada != null) {
                cerrarNotificacion(idAnulada);
            }
        }
        return new ContratoLicencia.RecordatorioResponse(fila.idLicencia(), req.activo(), idAlerta, estadoAlerta);
    }

    @Transactional
    public ContratoLicencia.FotoDto subirFoto(long usuario, String lado, String contentType, String nombreOriginal,
            byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Archivo vacío.", Set.of("file"));
        }
        ValidadorLicencia.exigirPresignUpload(contentType, bytes.length, nombreOriginal);
        exigirCifrado();
        String ladoNorm = normalizarLado(lado);
        String nombre = RutasDocumentoLicencia.nombreSeguro(nombreOriginal);
        String key = RutasDocumentoLicencia.claveTemp(usuario, ladoNorm, nombre);
        String ct = contentType == null ? "application/octet-stream" : contentType.strip();
        try {
            byte[] cifrado = cryptoGate.sellarBytes(bytes, aadFoto(key));
            almacenamiento.guardar(key, cifrado, ct);
            return new ContratoLicencia.FotoDto(key, ct, bytes.length, nombreOriginal, true);
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorLicencia(ex.codigo(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] leerFotoBytes(long usuario, String lado) {
        String ladoNorm = normalizarLado(lado);
        var actual = dao.obtenerActual(usuario)
                .orElseThrow(() -> new ErrorLicencia("LICENCIA_NO_ENCONTRADA", "No tienes licencia registrada."));
        String key;
        String ct;
        boolean cifrado;
        if ("FRONTAL".equals(ladoNorm)) {
            key = actual.fotoFrontalKey();
            ct = actual.fotoFrontalContentType();
            cifrado = actual.documentoCifrado();
        } else {
            key = actual.fotoPosteriorKey();
            ct = actual.fotoPosteriorContentType();
            cifrado = actual.documentoCifrado();
        }
        if (key == null || key.isBlank()) {
            throw new ErrorLicencia("FOTO_NO_DISPONIBLE", "No hay foto " + ladoNorm.toLowerCase(Locale.ROOT) + ".");
        }
        if (!RutasDocumentoLicencia.perteneceAUsuario(key, usuario)) {
            throw new ErrorLicencia("FOTO_NO_AUTORIZADA", "Ruta de foto no válida.");
        }
        try {
            byte[] almacenado = almacenamiento.leer(key);
            if (cifrado) {
                try {
                    return cryptoGate.abrirBytes(almacenado, aadFoto(key));
                } catch (RuntimeException ignorada) {
                    // Fallback USB/legacy: devolver bytes crudos si el AAD/clave no coincide.
                    return almacenado;
                }
            }
            try {
                return cryptoGate.abrirBytes(almacenado, aadFoto(key));
            } catch (RuntimeException ignorada) {
                return almacenado;
            }
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorLicencia(ex.codigo(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public FilaFotoMetadatos metadatosFoto(long usuario, String lado) {
        String ladoNorm = normalizarLado(lado);
        var actual = dao.obtenerActual(usuario).orElseThrow(
                () -> new ErrorLicencia("LICENCIA_NO_ENCONTRADA", "No tienes licencia registrada."));
        if ("FRONTAL".equals(ladoNorm)) {
            return new FilaFotoMetadatos(actual.fotoFrontalContentType(), actual.fotoFrontalNombre());
        }
        return new FilaFotoMetadatos(actual.fotoPosteriorContentType(), actual.fotoPosteriorNombre());
    }

    public record FilaFotoMetadatos(String contentType, String nombreOriginal) { }

    private ContratoLicencia.LicenciaDto dtoPublico(LicenciaJdbcDao.FilaLicencia fila, LocalDate hoy) {
        String numero = abrirNumero(fila);
        return new ContratoLicencia.LicenciaDto(
                fila.idLicencia(),
                fila.categoria(),
                numero,
                fila.fechaEmision(),
                fila.fechaVencimiento(),
                fila.vigencia(),
                ValidadorLicencia.estadoVigencia(fila.fechaVencimiento(), hoy),
                fila.recordatorioActivo(),
                fila.fotoFrontalKey() != null && !fila.fotoFrontalKey().isBlank(),
                fila.fotoPosteriorKey() != null && !fila.fotoPosteriorKey().isBlank(),
                fila.versionFila());
    }

    private FotoResuelta resolverFotoNueva(long usuario, Long idLicencia, ContratoLicencia.FotoDto foto, String lado) {
        if (foto == null) {
            return fotoVacia();
        }
        if (!RutasDocumentoLicencia.perteneceAUsuario(foto.objectKey(), usuario)) {
            throw new ErrorLicencia("FOTO_NO_AUTORIZADA", "La ruta de la foto no es válida.");
        }
        try {
            if (!almacenamiento.confirmarExistencia(foto.objectKey())) {
                throw new ErrorLicencia("FOTO_NO_DISPONIBLE", "Sube la foto antes de confirmar.");
            }
        } catch (ErrorAlmacenamiento ex) {
            throw new ErrorLicencia(ex.codigo(), ex.getMessage());
        }
        boolean cifrado = Boolean.TRUE.equals(foto.cifrado());
        return new FotoResuelta(foto.objectKey(), foto.contentType(), foto.tamanoBytes(), foto.nombreOriginal(), cifrado);
    }

    private FotoResuelta resolverFotoEditar(
            long usuario,
            LicenciaJdbcDao.FilaLicencia actual,
            ContratoLicencia.AccionFoto accion,
            ContratoLicencia.FotoDto nueva,
            boolean frontal) {
        return switch (accion) {
            case MANTENER -> campoFoto(actual, frontal);
            case QUITAR -> fotoVacia();
            case REEMPLAZAR -> resolverFotoNueva(usuario, actual.idLicencia(), nueva, frontal ? "FRONTAL" : "POSTERIOR");
        };
    }

    private static FotoResuelta campoFoto(LicenciaJdbcDao.FilaLicencia actual, boolean frontal) {
        if (frontal) {
            return new FotoResuelta(
                    actual.fotoFrontalKey(), actual.fotoFrontalContentType(),
                    actual.fotoFrontalTamano(), actual.fotoFrontalNombre(), actual.documentoCifrado());
        }
        return new FotoResuelta(
                actual.fotoPosteriorKey(), actual.fotoPosteriorContentType(),
                actual.fotoPosteriorTamano(), actual.fotoPosteriorNombre(), actual.documentoCifrado());
    }

    private static FotoResuelta fotoVacia() {
        return new FotoResuelta(null, null, null, null, false);
    }

    private static void exigirAmbasFotos(FotoResuelta frontal, FotoResuelta posterior) {
        if (frontal.objectKey() == null || posterior.objectKey() == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "Debes conservar foto frontal y posterior.");
        }
    }

    private void exigirFotosRenovacionNoReutilizadas(
            LicenciaJdbcDao.FilaLicencia anterior, ContratoLicencia.RenovarRequest req) {
        if (req.fotoFrontal() != null && Objects.equals(req.fotoFrontal().objectKey(), anterior.fotoFrontalKey())) {
            throw new ErrorLicencia("FOTO_NO_AUTORIZADA", "Renueva con fotos nuevas; no reutilices claves anteriores.",
                    Set.of("fotoFrontal"));
        }
        if (req.fotoPosterior() != null && Objects.equals(req.fotoPosterior().objectKey(), anterior.fotoPosteriorKey())) {
            throw new ErrorLicencia("FOTO_NO_AUTORIZADA", "Renueva con fotos nuevas; no reutilices claves anteriores.",
                    Set.of("fotoPosterior"));
        }
    }

    private void limpiarFotoSiCorresponde(String claveAnterior, String claveNueva, ContratoLicencia.AccionFoto accion) {
        if (claveAnterior == null || claveAnterior.isBlank()) {
            return;
        }
        if (accion == ContratoLicencia.AccionFoto.MANTENER) {
            return;
        }
        if (accion == ContratoLicencia.AccionFoto.REEMPLAZAR && Objects.equals(claveAnterior, claveNueva)) {
            return;
        }
        try {
            almacenamiento.borrarLogicoHuerfano(claveAnterior);
        } catch (ErrorAlmacenamiento ignored) {
            // fila ya apunta al nuevo objeto
        }
    }

    private boolean sinCambiosEditar(
            LicenciaJdbcDao.FilaLicencia actual,
            ContratoLicencia.EditarRequest req,
            FotoResuelta frontal,
            FotoResuelta posterior) {
        String numeroActual = abrirNumero(actual);
        boolean mismosDatos = Objects.equals(actual.categoria(), req.categoria())
                && Objects.equals(ValidadorLicencia.normalizarNumero(numeroActual), ValidadorLicencia.normalizarNumero(req.numero()))
                && Objects.equals(actual.fechaEmision(), req.fechaEmision())
                && Objects.equals(actual.fechaVencimiento(), req.fechaVencimiento());
        if (!mismosDatos) {
            return false;
        }
        return Objects.equals(actual.fotoFrontalKey(), frontal.objectKey())
                && Objects.equals(actual.fotoPosteriorKey(), posterior.objectKey());
    }

    private long activarAlerta(long usuario, long idLicencia, LocalDate fechaVencimiento) {
        desactivarAlertaSiActiva(idLicencia);
        LocalDate hoy = dao.hoyLima();
        LocalDate fechaBase = fechaVencimiento.minusDays(30);
        String firma = FirmaAlertaLicencia.calcular(idLicencia);
        String estado = dao.calcularEstadoInicial(fechaVencimiento, hoy);
        LocalDate fechaVencidaVerificable = "VENCIDA".equals(estado) ? hoy : null;
        try {
            long idAlerta = dao.insertarAlertaLicencia(
                    usuario, idLicencia, fechaBase, fechaVencimiento, estado, firma, fechaVencidaVerificable);
            dao.historialAlertaInicial(idAlerta, estado);
            return idAlerta;
        } catch (DataIntegrityViolationException ex) {
            String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
            if (detail != null && (detail.contains("uq_alerta_licencia_activa")
                    || detail.contains("uq_alerta_equivalente_activa"))) {
                throw new ErrorLicencia("ALERTA_LICENCIA_DUPLICADA", "Ya existe un recordatorio activo para tu licencia.");
            }
            throw new ErrorLicencia("LICENCIA_GUARDAR_FALLIDO", "No se pudo activar el recordatorio.");
        }
    }

    private Long desactivarAlertaSiActiva(long idLicencia) {
        return dao.alertaActivaPorLicencia(idLicencia).map(alerta -> {
            var version = dao.alertaVersion(alerta.idAlerta()).orElseThrow();
            dao.anularAlertaLicencia(version.idAlerta(), version.estado(), version.versionFila());
            return version.idAlerta();
        }).orElse(null);
    }

    private void cerrarAlertaPorRenovacion(long idLicenciaAnterior) {
        dao.alertaActivaPorLicencia(idLicenciaAnterior).ifPresent(alerta -> {
            var version = dao.alertaVersion(alerta.idAlerta()).orElseThrow();
            dao.cerrarAlertaLicenciaPorRenovacion(version.idAlerta(), version.estado(), version.versionFila());
            var n = notificaciones.getIfAvailable();
            if (n != null) {
                n.marcarAtendidaPorAlerta(version.idAlerta());
            }
        });
    }

    private void reconciliarAlerta(long usuario, long idLicencia, LocalDate fechaVencimiento) {
        if (dao.alertaActivaPorLicencia(idLicencia).isEmpty()) {
            return;
        }
        long idAlerta = activarAlerta(usuario, idLicencia, fechaVencimiento);
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
                throw new ErrorLicencia("OPERACION_EN_PROCESO", "No se pudo confirmar la operación.", true);
            }
        } catch (ErrorLicencia ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar respuesta licencia", ex);
        }
    }

    private ContratoLicencia.RegistrarResponse leerRegistroConfirmado(String payload) {
        try {
            return json.readValue(payload, ContratoLicencia.RegistrarResponse.class);
        } catch (Exception ex) {
            throw new ErrorLicencia("OPERACION_EN_PROCESO", "Resultado pendiente.", true);
        }
    }

    private ContratoLicencia.RenovarResponse leerRenovacionConfirmada(String payload) {
        try {
            return json.readValue(payload, ContratoLicencia.RenovarResponse.class);
        } catch (Exception ex) {
            throw new ErrorLicencia("OPERACION_EN_PROCESO", "Resultado pendiente.", true);
        }
    }

    private CamposSellados sellarNumero(long usuario, Long idLicencia, String numero) {
        try {
            var crypto = cryptoGate.exigir();
            String plano = ValidadorLicencia.normalizarNumero(numero);
            String blind = crypto.indiceCiego(plano);
            String aad = idLicencia == null ? "licencia|numero|u|" + usuario : aadNumero(idLicencia);
            String cifrado = crypto.sellar(plano, aad);
            return new CamposSellados(cifrado, blind, crypto.keyId());
        } catch (ErrorSoat ex) {
            throw new ErrorLicencia(ex.codigo(), ex.getMessage());
        }
    }

    private String blindDeNumero(String numero) {
        exigirCifrado();
        return cryptoGate.indiceCiego(ValidadorLicencia.normalizarNumero(numero));
    }

    private void exigirCifrado() {
        try {
            cryptoGate.exigir();
        } catch (ErrorSoat ex) {
            throw new ErrorLicencia(ex.codigo(), ex.getMessage());
        }
    }

    /** Alta usa AAD por usuario hasta tener id; lectura acepta ambos. */
    private String abrirNumero(LicenciaJdbcDao.FilaLicencia fila) {
        String porId = aadNumero(fila.idLicencia());
        try {
            return cryptoGate.abrirCampo(fila.numeroCifrado(), porId);
        } catch (RuntimeException primera) {
            try {
                return cryptoGate.abrirCampo(fila.numeroCifrado(), aadNumeroUsuario(fila.idUsuario()));
            } catch (RuntimeException ignorada) {
                throw primera;
            }
        }
    }

    private static String aadNumero(long idLicencia) {
        return "licencia|numero|" + idLicencia;
    }

    private static String aadNumeroUsuario(long idUsuario) {
        return "licencia|numero|u|" + idUsuario;
    }

    private static String aadFoto(String objectKey) {
        return "licencia|foto|" + objectKey;
    }

    private static String normalizarLado(String lado) {
        if (lado == null) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "lado obligatorio.", Set.of("lado"));
        }
        String n = lado.strip().toUpperCase(Locale.ROOT);
        if (!"FRONTAL".equals(n) && !"POSTERIOR".equals(n)) {
            throw new ErrorLicencia("DATOS_INVALIDOS", "lado debe ser FRONTAL o POSTERIOR.", Set.of("lado"));
        }
        return n;
    }

    private record CamposSellados(String numeroCifrado, String blind, String keyId) { }

    private record FotoResuelta(
            String objectKey, String contentType, Integer tamanoBytes, String nombreOriginal, boolean cifrado) { }
}