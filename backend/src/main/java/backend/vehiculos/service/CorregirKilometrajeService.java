package backend.vehiculos.service;

import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.ConsultaRegistroVehiculoDao;
import backend.vehiculos.dao.CorregirKilometrajeDao;
import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.domain.MotivoCorreccionKilometraje;
import backend.vehiculos.dto.ContextoCorreccionKilometraje;
import backend.vehiculos.dto.DatosCorregirKilometraje;
import backend.vehiculos.dto.DatosEstadoOperacionCorreccionKm;
import backend.vehiculos.dto.ResultadoCorregirKilometraje;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorregirKilometrajeService {
    private static final String TIPO = "CORREGIR_KM";

    private final CondicionesRegistroVehiculoDao condiciones;
    private final CorregirKilometrajeDao correccion;
    private final OperacionVehiculoDao operaciones;
    private final ConsultaRegistroVehiculoDao consultas;
    private final HuellaCorregirKilometraje huellas;
    private final ObjectMapper json;

    public CorregirKilometrajeService(
            CondicionesRegistroVehiculoDao condiciones,
            CorregirKilometrajeDao correccion,
            OperacionVehiculoDao operaciones,
            ConsultaRegistroVehiculoDao consultas,
            HuellaCorregirKilometraje huellas,
            ObjectMapper json
    ) {
        this.condiciones = condiciones;
        this.correccion = correccion;
        this.operaciones = operaciones;
        this.consultas = consultas;
        this.huellas = huellas;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ContextoCorreccionKilometraje consultarContexto(long idUsuario, long idVehiculo, long idLectura) {
        condiciones.exigirCuentaActiva(idUsuario);
        if (idVehiculo <= 0 || idLectura <= 0) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        var vehiculo = correccion.consultarVehiculoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if (!"ACTIVO".equals(vehiculo.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }
        var lecturas = correccion.listarLecturasEfectivas(idVehiculo);
        if (lecturas.isEmpty()) {
            return noElegible(vehiculo, idLectura, "KILOMETRAJE_NO_DISPONIBLE");
        }
        var ultima = lecturas.getFirst();
        if (ultima.idLectura() != idLectura) {
            throw new ErrorRegistroVehiculo("LECTURA_NO_ULTIMA", Set.of("idLectura"));
        }
        if (correccion.yaFueCorregida(idLectura)) {
            return noElegible(vehiculo, idLectura, "CORRECCION_YA_APLICADA");
        }
        if (lecturas.size() < 2) {
            return noElegible(vehiculo, idLectura, "CORRECCION_NO_DISPONIBLE");
        }
        var penultima = lecturas.get(1);
        BigDecimal pen = entero(penultima.valorKm());
        BigDecimal ult = entero(ultima.valorKm());
        if (ult.compareTo(pen.add(BigDecimal.ONE)) <= 0) {
            return new ContextoCorreccionKilometraje(
                    vehiculo.idVehiculo(), vehiculo.placa(), vehiculo.marca(), vehiculo.modelo(),
                    ultima.idLectura(), pen, ult, vehiculo.versionKm(),
                    false, "CORRECCION_NO_DISPONIBLE");
        }
        return new ContextoCorreccionKilometraje(
                vehiculo.idVehiculo(), vehiculo.placa(), vehiculo.marca(), vehiculo.modelo(),
                ultima.idLectura(), pen, ult, vehiculo.versionKm(),
                true, null);
    }

    @Transactional(readOnly = true)
    public DatosEstadoOperacionCorreccionKm consultarOperacion(long idUsuario, UUID idOperacion) {
        condiciones.exigirCuentaActiva(idUsuario);
        var operacion = consultas.consultarOperacionPorTipo(idOperacion, idUsuario, TIPO)
                .orElseThrow(() -> new ErrorRegistroVehiculo("OPERACION_NO_ENCONTRADA"));
        ResultadoCorregirKilometraje resultado = null;
        if ("CONFIRMADA".equals(operacion.estado())) {
            resultado = recuperar(operacion.respuestaJson());
        }
        return new DatosEstadoOperacionCorreccionKm(idOperacion, operacion.estado(), resultado);
    }

    @Transactional
    public ResultadoCorregirKilometraje registrar(long idUsuario, long idVehiculo, DatosCorregirKilometraje datos) {
        if (datos == null || datos.idOperacion() == null) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("solicitud"));
        }
        condiciones.exigirCuentaActiva(idUsuario);
        if (idVehiculo <= 0) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        String hash = huellas.calcular(idVehiculo, datos);
        boolean nueva = operaciones.reservar(datos.idOperacion(), idUsuario, TIPO, hash);
        var operacion = operaciones.buscarParaActualizar(datos.idOperacion(), idUsuario, TIPO)
                .orElseThrow(() -> new ErrorRegistroVehiculo("CONFLICTO_IDEMPOTENCIA"));
        if (!hash.equals(operacion.hashSolicitud())) {
            throw new ErrorRegistroVehiculo("CONFLICTO_IDEMPOTENCIA");
        }
        if ("CONFIRMADA".equals(operacion.estado())) {
            return recuperar(operacion.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(operacion.estado())) {
            throw new ErrorRegistroVehiculo("OPERACION_EN_PROCESO");
        }

        Set<String> errores = validarEntrada(datos);
        if (!errores.isEmpty()) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", errores);
        }
        String motivo = MotivoCorreccionKilometraje.normalizar(datos.motivo());
        if (motivo == null) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("motivo"));
        }

        var vehiculo = correccion.bloquearVehiculoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if (!"ACTIVO".equals(vehiculo.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }
        if (vehiculo.versionKm() != datos.versionBase()) {
            throw new ErrorRegistroVehiculo("KM_OBSOLETO", Set.of("versionBase"));
        }

        var lecturas = correccion.listarLecturasEfectivas(idVehiculo);
        if (lecturas.size() < 2) {
            throw new ErrorRegistroVehiculo("CORRECCION_NO_DISPONIBLE");
        }
        var ultima = lecturas.getFirst();
        var penultima = lecturas.get(1);
        if (ultima.idLectura() != datos.idLecturaBase()) {
            throw new ErrorRegistroVehiculo("LECTURA_NO_ULTIMA", Set.of("idLecturaBase"));
        }
        if (correccion.yaFueCorregida(ultima.idLectura())) {
            throw new ErrorRegistroVehiculo("CORRECCION_YA_APLICADA");
        }
        correccion.bloquearLectura(idVehiculo, ultima.idLectura())
                .orElseThrow(() -> new ErrorRegistroVehiculo("KILOMETRAJE_NO_DISPONIBLE"));

        BigDecimal kmPenultimo = entero(penultima.valorKm());
        BigDecimal kmUltimo = entero(ultima.valorKm());
        BigDecimal kmCorregido = BigDecimal.valueOf(datos.kmCorregido()).setScale(0, RoundingMode.UNNECESSARY);

        if (kmUltimo.compareTo(kmPenultimo.add(BigDecimal.ONE)) <= 0) {
            throw new ErrorRegistroVehiculo("CORRECCION_NO_DISPONIBLE");
        }
        if (kmCorregido.compareTo(kmPenultimo) <= 0 || kmCorregido.compareTo(kmUltimo) >= 0) {
            throw new ErrorRegistroVehiculo("KM_INTERVALO_INVALIDO", Set.of("kmCorregido"));
        }
        if (correccion.hayMantenimientoIncompatible(idVehiculo, kmCorregido)) {
            throw new ErrorRegistroVehiculo("REFERENCIA_INCOMPATIBLE", Set.of("kmCorregido"));
        }

        long idCorreccion = correccion.registrarCorreccion(
                ultima.idLectura(), idVehiculo, idUsuario,
                kmUltimo, kmCorregido, kmPenultimo, motivo, datos.idOperacion());
        correccion.actualizarValorLectura(ultima.idLectura(), kmCorregido);
        if (!correccion.incrementarVersionSiCoincide(idVehiculo, datos.versionBase())) {
            throw new ErrorRegistroVehiculo("KM_OBSOLETO", Set.of("versionBase"));
        }

        var resultado = new ResultadoCorregirKilometraje(
                datos.idOperacion(),
                idVehiculo,
                ultima.idLectura(),
                idCorreccion,
                kmUltimo,
                kmCorregido,
                kmPenultimo,
                motivo,
                correccion.fechaCorreccion(idCorreccion),
                ultima.fechaLectura(),
                "CONFIRMADA");
        if (!operaciones.confirmar(datos.idOperacion(), idUsuario, TIPO, hash, serializar(resultado))) {
            throw new IllegalStateException("No se pudo confirmar la operación RF16");
        }
        return resultado;
    }

    private ContextoCorreccionKilometraje noElegible(
            CorregirKilometrajeDao.VehiculoActivoResumen vehiculo, long idLectura, String codigo
    ) {
        return new ContextoCorreccionKilometraje(
                vehiculo.idVehiculo(), vehiculo.placa(), vehiculo.marca(), vehiculo.modelo(),
                idLectura, null, null, vehiculo.versionKm(), false, codigo);
    }

    private Set<String> validarEntrada(DatosCorregirKilometraje datos) {
        var errores = new java.util.HashSet<String>();
        if (datos.kmCorregido() == null || datos.kmCorregido() <= 0) {
            errores.add("kmCorregido");
        }
        if (datos.motivo() == null || datos.motivo().isBlank()) {
            errores.add("motivo");
        }
        if (datos.idLecturaBase() == null || datos.idLecturaBase() <= 0) {
            errores.add("idLecturaBase");
        }
        if (datos.versionBase() == null || datos.versionBase() < 0) {
            errores.add("versionBase");
        }
        return errores;
    }

    private static BigDecimal entero(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        try {
            return valor.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            return valor.setScale(0, RoundingMode.DOWN);
        }
    }

    private ResultadoCorregirKilometraje recuperar(String respuestaJson) {
        if (respuestaJson == null) {
            throw new IllegalStateException("Operación confirmada sin respuesta RF16");
        }
        try {
            var resultado = json.readValue(respuestaJson, ResultadoCorregirKilometraje.class);
            if (resultado == null) {
                throw new IllegalStateException("Resultado RF16 inválido");
            }
            return resultado;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo recuperar el resultado RF16", ex);
        }
    }

    private String serializar(ResultadoCorregirKilometraje resultado) {
        try {
            return json.writeValueAsString(resultado);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el resultado RF16", ex);
        }
    }
}
