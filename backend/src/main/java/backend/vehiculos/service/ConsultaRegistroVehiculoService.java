package backend.vehiculos.service;

import backend.vehiculos.dao.CatalogosVehiculoDao;
import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.ConsultaRegistroVehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosCupoRegistro;
import backend.vehiculos.dto.DatosEstadoOperacionVehiculo;
import backend.vehiculos.dto.ResultadoRegistroVehiculo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ConsultaRegistroVehiculoService {
    private final CondicionesRegistroVehiculoDao condiciones;
    private final CatalogosVehiculoDao catalogos;
    private final ConsultaRegistroVehiculoDao consultas;
    private final ObjectMapper json;

    public ConsultaRegistroVehiculoService(
            CondicionesRegistroVehiculoDao condiciones,
            CatalogosVehiculoDao catalogos,
            ConsultaRegistroVehiculoDao consultas,
            ObjectMapper json
    ) {
        this.condiciones = condiciones;
        this.catalogos = catalogos;
        this.consultas = consultas;
        this.json = json;
    }

    public DatosCatalogosVehiculo consultarCatalogos(long idUsuarioAutenticado) {
        condiciones.exigirCuentaActiva(idUsuarioAutenticado);
        return catalogos.consultar();
    }

    public DatosCupoRegistro consultarCupo(long idUsuarioAutenticado) {
        condiciones.exigirCuentaActiva(idUsuarioAutenticado);
        return consultas.consultarCupo(idUsuarioAutenticado);
    }

    public DatosEstadoOperacionVehiculo consultarOperacion(
            long idUsuarioAutenticado, UUID idOperacion
    ) {
        condiciones.exigirCuentaActiva(idUsuarioAutenticado);
        var operacion = consultas.consultarOperacion(idOperacion, idUsuarioAutenticado)
                .orElseThrow(() -> new ErrorRegistroVehiculo("OPERACION_NO_ENCONTRADA"));
        ResultadoRegistroVehiculo resultado = null;
        if ("CONFIRMADA".equals(operacion.estado())) {
            if (operacion.respuestaJson() == null) {
                throw new IllegalStateException("Operación confirmada sin respuesta");
            }
            try {
                resultado = json.readValue(operacion.respuestaJson(), ResultadoRegistroVehiculo.class);
                if (resultado == null) {
                    throw new IllegalStateException("Resultado confirmado inválido");
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("No se pudo recuperar el resultado de la operación", ex);
            }
        }
        return new DatosEstadoOperacionVehiculo(idOperacion, operacion.estado(), resultado);
    }
}
