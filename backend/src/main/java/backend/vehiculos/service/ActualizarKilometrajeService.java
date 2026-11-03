package backend.vehiculos.service;

import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.ConsultaRegistroVehiculoDao;
import backend.vehiculos.dao.KilometrajeVehiculoDao;
import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.ContextoKilometrajeActual;
import backend.vehiculos.dto.DatosActualizarKilometraje;
import backend.vehiculos.dto.DatosEstadoOperacionKilometraje;
import backend.vehiculos.dto.ResultadoActualizarKilometraje;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActualizarKilometrajeService {
    private static final String TIPO = "ACTUALIZAR_KM";

    private final CondicionesRegistroVehiculoDao condiciones;
    private final KilometrajeVehiculoDao kilometraje;
    private final OperacionVehiculoDao operaciones;
    private final ConsultaRegistroVehiculoDao consultas;
    private final HuellaActualizarKilometraje huellas;
    private final ObjectMapper json;

    public ActualizarKilometrajeService(
            CondicionesRegistroVehiculoDao condiciones,
            KilometrajeVehiculoDao kilometraje,
            OperacionVehiculoDao operaciones,
            ConsultaRegistroVehiculoDao consultas,
            HuellaActualizarKilometraje huellas,
            ObjectMapper json
    ) {
        this.condiciones = condiciones;
        this.kilometraje = kilometraje;
        this.operaciones = operaciones;
        this.consultas = consultas;
        this.huellas = huellas;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ContextoKilometrajeActual consultarActual(long idUsuario, long idVehiculo) {
        condiciones.exigirCuentaActiva(idUsuario);
        if (idVehiculo <= 0) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        var contexto = kilometraje.consultarContextoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if (contexto.idLectura() <= 0 || contexto.kmActual() <= 0) {
            throw new ErrorRegistroVehiculo("KILOMETRAJE_NO_DISPONIBLE");
        }
        return contexto;
    }

    @Transactional(readOnly = true)
    public DatosEstadoOperacionKilometraje consultarOperacion(long idUsuario, UUID idOperacion) {
        condiciones.exigirCuentaActiva(idUsuario);
        var operacion = consultas.consultarOperacionPorTipo(idOperacion, idUsuario, TIPO)
                .orElseThrow(() -> new ErrorRegistroVehiculo("OPERACION_NO_ENCONTRADA"));
        ResultadoActualizarKilometraje resultado = null;
        if ("CONFIRMADA".equals(operacion.estado())) {
            resultado = recuperar(operacion.respuestaJson());
        }
        return new DatosEstadoOperacionKilometraje(idOperacion, operacion.estado(), resultado);
    }

    @Transactional
    public ResultadoActualizarKilometraje registrar(long idUsuario, long idVehiculo, DatosActualizarKilometraje datos) {
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

        var vehiculo = kilometraje.bloquearVehiculoPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if (!"ACTIVO".equals(vehiculo.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }
        if (vehiculo.versionKm() != datos.versionBase()) {
            throw new ErrorRegistroVehiculo("KM_OBSOLETO", Set.of("versionBase"));
        }

        var vigente = consultarActual(idUsuario, idVehiculo);
        if (vigente.idLectura() != datos.idLecturaBase()) {
            throw new ErrorRegistroVehiculo("KM_OBSOLETO", Set.of("idLecturaBase"));
        }
        if (datos.kmNuevo() <= vigente.kmActual()) {
            throw new ErrorRegistroVehiculo("KM_NO_SUPERIOR", Set.of("kmNuevo"));
        }

        long idLectura = kilometraje.insertarLecturaActualizacion(idVehiculo, datos.kmNuevo(), datos.idOperacion());
        if (!kilometraje.incrementarVersionSiCoincide(idVehiculo, datos.versionBase())) {
            throw new ErrorRegistroVehiculo("KM_OBSOLETO", Set.of("versionBase"));
        }
        var resultado = new ResultadoActualizarKilometraje(
                datos.idOperacion(),
                idVehiculo,
                idLectura,
                datos.kmNuevo(),
                kilometraje.fechaLectura(idLectura),
                "CONFIRMADA");
        if (!operaciones.confirmar(datos.idOperacion(), idUsuario, TIPO, hash, serializar(resultado))) {
            throw new IllegalStateException("No se pudo confirmar la operación RF14");
        }
        return resultado;
    }

    private Set<String> validarEntrada(DatosActualizarKilometraje datos) {
        var errores = new java.util.HashSet<String>();
        if (datos.kmNuevo() == null || datos.kmNuevo() <= 0) {
            errores.add("kmNuevo");
        }
        if (datos.idLecturaBase() == null || datos.idLecturaBase() <= 0) {
            errores.add("idLecturaBase");
        }
        if (datos.versionBase() == null || datos.versionBase() < 0) {
            errores.add("versionBase");
        }
        return errores;
    }

    private ResultadoActualizarKilometraje recuperar(String respuestaJson) {
        if (respuestaJson == null) {
            throw new IllegalStateException("Operación confirmada sin respuesta RF14");
        }
        try {
            var resultado = json.readValue(respuestaJson, ResultadoActualizarKilometraje.class);
            if (resultado == null) {
                throw new IllegalStateException("Resultado RF14 inválido");
            }
            return resultado;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo recuperar el resultado RF14", ex);
        }
    }

    private String serializar(ResultadoActualizarKilometraje resultado) {
        try {
            return json.writeValueAsString(resultado);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el resultado RF14", ex);
        }
    }
}
