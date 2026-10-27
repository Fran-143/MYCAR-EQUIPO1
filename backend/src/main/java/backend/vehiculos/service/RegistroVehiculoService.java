package backend.vehiculos.service;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.dao.RegistroInicialVehiculoDao;
import backend.vehiculos.dao.VehiculoDao;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import backend.vehiculos.dto.ResultadoRegistroVehiculo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class RegistroVehiculoService {
    private static final String TIPO = "REGISTRAR_VEHICULO";
    private final ValidacionRegistroVehiculoService validacion;
    private final VehiculoDao vehiculos;
    private final OperacionVehiculoDao operaciones;
    private final CondicionesRegistroVehiculoDao condiciones;
    private final RegistroInicialVehiculoDao inicial;
    private final HuellaRegistroVehiculo huellas;
    private final ObjectMapper json;

    public RegistroVehiculoService(
            ValidacionRegistroVehiculoService validacion,
            VehiculoDao vehiculos,
            OperacionVehiculoDao operaciones,
            CondicionesRegistroVehiculoDao condiciones,
            RegistroInicialVehiculoDao inicial,
            HuellaRegistroVehiculo huellas,
            ObjectMapper json
    ) {
        this.validacion = validacion;
        this.vehiculos = vehiculos;
        this.operaciones = operaciones;
        this.condiciones = condiciones;
        this.inicial = inicial;
        this.huellas = huellas;
        this.json = json;
    }

    /**
     * Entrada INTERNA: el llamador debe resolver el usuario desde la sesión válida.
     * Nunca obtener idUsuarioAutenticado del JSON ni de un parámetro libre del cliente.
     * No representa todavía un endpoint protegido ni valida tokens/sesiones.
     */
    @Transactional
    public ResultadoRegistroVehiculo registrar(
            long idUsuarioAutenticado, DatosRegistroVehiculo datos
    ) {
        if (datos == null) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("solicitud"));
        }
        if (datos.idOperacion() == null) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("idOperacion"));
        }
        condiciones.exigirCuentaActiva(idUsuarioAutenticado);
        String hash = huellas.calcular(datos);

        boolean nueva = operaciones.reservar(
                datos.idOperacion(), idUsuarioAutenticado, TIPO, hash);
        var operacion = operaciones.buscarParaActualizar(
                datos.idOperacion(), idUsuarioAutenticado, TIPO
        ).orElseThrow(() -> new ErrorRegistroVehiculo("CONFLICTO_IDEMPOTENCIA"));

        if (!hash.equals(operacion.hashSolicitud())) {
            throw new ErrorRegistroVehiculo("CONFLICTO_IDEMPOTENCIA");
        }
        if ("CONFIRMADA".equals(operacion.estado())) {
            return recuperar(operacion.respuestaJson());
        }
        if (!nueva || !"EN_PROCESO".equals(operacion.estado())) {
            throw new ErrorRegistroVehiculo("OPERACION_EN_PROCESO");
        }

        Set<String> errores = validacion.validar(datos);
        if (!errores.isEmpty()) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", errores);
        }
        int limite = condiciones.bloquearYConsultarLimiteFree(idUsuarioAutenticado);
        long registrados = vehiculos.contarRegistrados(idUsuarioAutenticado);
        if (registrados >= limite) {
            throw new ErrorRegistroVehiculo("LIMITE_FREE");
        }
        if (vehiculos.existePlaca(datos.placa())) {
            throw new ErrorRegistroVehiculo("PLACA_DUPLICADA", Set.of("placa"));
        }
        if (vehiculos.existeVin(datos.numeroVin())) {
            throw new ErrorRegistroVehiculo("VIN_DUPLICADO", Set.of("numeroVin"));
        }

        boolean principal = registrados == 0;
        long idVehiculo;
        try {
            idVehiculo = vehiculos.insertarVehiculo(idUsuarioAutenticado, datos, principal);
        } catch (DuplicateKeyException ex) {
            // Cubre una carrera entre propietarios después del SELECT preventivo.
            // La excepción sale del proxy transaccional y revierte toda la operación.
            throw new ErrorRegistroVehiculo("CONFLICTO_REGISTRO");
        }
        var lectura = inicial.registrar(
                idVehiculo, datos.kilometrajeInicial(), datos.idOperacion());
        var resultado = new ResultadoRegistroVehiculo(
                datos.idOperacion(), idVehiculo, datos.placa(), principal,
                datos.kilometrajeInicial(), lectura.idLecturaKm(), lectura.fechaLectura(), 0);
        if (!operaciones.confirmar(datos.idOperacion(), idUsuarioAutenticado,
                TIPO, hash, serializar(resultado))) {
            throw new IllegalStateException("No se pudo confirmar la operación de registro");
        }
        return resultado;
    }

    private String serializar(ResultadoRegistroVehiculo resultado) {
        try {
            return json.writeValueAsString(resultado);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo guardar la respuesta del registro", ex);
        }
    }

    private ResultadoRegistroVehiculo recuperar(String respuestaJson) {
        if (respuestaJson == null) {
            throw new IllegalStateException("Operación confirmada sin respuesta");
        }
        try {
            ResultadoRegistroVehiculo resultado = json.readValue(
                    respuestaJson, ResultadoRegistroVehiculo.class);
            if (resultado == null) {
                throw new IllegalStateException("Respuesta confirmada inválida");
            }
            return resultado;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo recuperar la respuesta confirmada", ex);
        }
    }
}
