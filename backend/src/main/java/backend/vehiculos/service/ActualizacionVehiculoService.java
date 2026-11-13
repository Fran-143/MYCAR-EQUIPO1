package backend.vehiculos.service;

import backend.vehiculos.dao.CatalogosVehiculoDao;
import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.VehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.domain.ValidadorIdentificacionVehiculo;
import backend.vehiculos.domain.ValidadorVehiculo;
import backend.vehiculos.dto.DatosActualizacionVehiculo;
import backend.vehiculos.dto.DatosDetalleVehiculo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class ActualizacionVehiculoService {
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final CondicionesRegistroVehiculoDao condiciones;
    private final VehiculoDao vehiculos;
    private final CatalogosVehiculoDao catalogos;
    private final ConsultaRegistroVehiculoService consultas;
    private final PresenciaTiveVehiculo presenciaTive;

    public ActualizacionVehiculoService(
            CondicionesRegistroVehiculoDao condiciones,
            VehiculoDao vehiculos,
            CatalogosVehiculoDao catalogos,
            ConsultaRegistroVehiculoService consultas,
            PresenciaTiveVehiculo presenciaTive
    ) {
        this.condiciones = condiciones;
        this.vehiculos = vehiculos;
        this.catalogos = catalogos;
        this.consultas = consultas;
        this.presenciaTive = presenciaTive;
    }

    @Transactional
    public DatosDetalleVehiculo actualizar(
            long idUsuarioAutenticado, long idVehiculo, DatosActualizacionVehiculo datos
    ) {
        if (datos == null) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", Set.of("solicitud"));
        }
        if (idVehiculo <= 0) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }
        condiciones.exigirCuentaActiva(idUsuarioAutenticado);

        var actual = vehiculos.obtenerEditablePropio(idUsuarioAutenticado, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if (!"ACTIVO".equals(actual.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("VEHICULO_INACTIVO");
        }

        DatosActualizacionVehiculo normalizados = normalizarElectricos(datos);
        Set<String> errores = validar(normalizados);
        if (!errores.isEmpty()) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS", errores);
        }

        boolean tieneTive = presenciaTive.existeParaVehiculo(idVehiculo);
        if (tieneTive && identificadoresDocumentalesCambiaron(actual, normalizados)) {
            throw new ErrorRegistroVehiculo(
                    "IDENTIFICADORES_BLOQUEADOS_TIVE",
                    Set.of("numeroVin", "numeroMotor", "numeroSerie")
            );
        }

        if (!tieneTive && vehiculos.existeVinEnOtro(normalizados.numeroVin(), idVehiculo)) {
            throw new ErrorRegistroVehiculo("VIN_DUPLICADO", Set.of("numeroVin"));
        }

        DatosActualizacionVehiculo aPersistir = tieneTive
                ? conservarIdentificadores(normalizados, actual)
                : normalizados;

        if (vehiculos.existeVehiculoIdenticoEnOtro(idUsuarioAutenticado, idVehiculo, aPersistir)) {
            throw new ErrorRegistroVehiculo("VEHICULO_DUPLICADO", Set.of(
                    "marca", "modelo", "usoVehiculoCodigo", "anioFabricacion", "color"));
        }

        if (mismosDatosGenerales(actual, aPersistir)) {
            throw new ErrorRegistroVehiculo("SIN_CAMBIOS");
        }

        try {
            vehiculos.actualizarDatosGenerales(idUsuarioAutenticado, idVehiculo, aPersistir);
        } catch (DuplicateKeyException ex) {
            throw new ErrorRegistroVehiculo("VIN_DUPLICADO", Set.of("numeroVin"));
        }

        return consultas.consultarDetalle(idUsuarioAutenticado, idVehiculo);
    }

    private static boolean mismosDatosGenerales(
            VehiculoDao.VehiculoEditable actual, DatosActualizacionVehiculo datos
    ) {
        return Objects.equals(actual.marca(), datos.marca())
                && Objects.equals(actual.modelo(), datos.modelo())
                && Objects.equals(actual.usoVehiculoCodigo(), datos.usoVehiculoCodigo())
                && Objects.equals(actual.anioFabricacion(), datos.anioFabricacion())
                && Objects.equals(actual.color(), datos.color())
                && Objects.equals(actual.idTipoMotor(), datos.idTipoMotor())
                && Objects.equals(actual.idCombustible(), datos.idCombustible())
                && Objects.equals(actual.idTipoAceite(), datos.idTipoAceite())
                && Objects.equals(actual.tipoMotorOtro(), datos.tipoMotorOtro())
                && Objects.equals(actual.combustibleOtro(), datos.combustibleOtro())
                && Objects.equals(actual.tipoAceiteOtro(), datos.tipoAceiteOtro())
                && Objects.equals(actual.numeroVin(), datos.numeroVin())
                && Objects.equals(actual.numeroSerie(), datos.numeroSerie())
                && Objects.equals(actual.numeroMotor(), datos.numeroMotor());
    }

    private Set<String> validar(DatosActualizacionVehiculo datos) {
        int anioActual = Year.now(ZONA).getValue();
        var cats = catalogos.consultar();
        Set<String> errores = new LinkedHashSet<>(
                ValidadorVehiculo.validarDatosGeneralesEdicion(datos, anioActual)
        );
        errores.addAll(ValidadorVehiculo.validarMotor(datos, cats));

        boolean usoExiste = cats.usos().stream()
                .anyMatch(uso -> Objects.equals(uso.codigo(), datos.usoVehiculoCodigo()));
        if (!usoExiste) {
            errores.add("usoVehiculoCodigo");
        }
        if (!ValidadorIdentificacionVehiculo.esVinValido(datos.numeroVin())) {
            errores.add("numeroVin");
        }
        if (!ValidadorIdentificacionVehiculo.esNumeroMotorValido(datos.numeroMotor())) {
            errores.add("numeroMotor");
        }
        if (!ValidadorIdentificacionVehiculo.esNumeroSerieValido(datos.numeroSerie())) {
            errores.add("numeroSerie");
        }
        return Set.copyOf(errores);
    }

    private DatosActualizacionVehiculo normalizarElectricos(DatosActualizacionVehiculo datos) {
        var cats = catalogos.consultar();
        var motor = cats.tiposMotor().stream()
                .filter(t -> Objects.equals(t.idTipoMotor(), datos.idTipoMotor()))
                .findFirst()
                .orElse(null);
        if (motor != null && motor.esElectrico()) {
            return new DatosActualizacionVehiculo(
                    datos.marca(), datos.modelo(), datos.usoVehiculoCodigo(),
                    datos.anioFabricacion(), datos.color(), datos.idTipoMotor(),
                    null, datos.numeroVin(), datos.numeroSerie(), null,
                    null, datos.tipoMotorOtro(), null, null
            );
        }
        return datos;
    }

    private static boolean identificadoresDocumentalesCambiaron(
            VehiculoDao.VehiculoEditable actual, DatosActualizacionVehiculo datos
    ) {
        return !Objects.equals(actual.numeroVin(), datos.numeroVin())
                || !Objects.equals(actual.numeroMotor(), datos.numeroMotor())
                || !Objects.equals(actual.numeroSerie(), datos.numeroSerie());
    }

    private static DatosActualizacionVehiculo conservarIdentificadores(
            DatosActualizacionVehiculo datos, VehiculoDao.VehiculoEditable actual
    ) {
        return new DatosActualizacionVehiculo(
                datos.marca(), datos.modelo(), datos.usoVehiculoCodigo(),
                datos.anioFabricacion(), datos.color(), datos.idTipoMotor(),
                datos.idCombustible(), actual.numeroVin(), actual.numeroSerie(),
                actual.numeroMotor(), datos.idTipoAceite(), datos.tipoMotorOtro(),
                datos.combustibleOtro(), datos.tipoAceiteOtro()
        );
    }
}
