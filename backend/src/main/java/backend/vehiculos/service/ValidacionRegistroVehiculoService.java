package backend.vehiculos.service;

import backend.vehiculos.dao.CatalogosVehiculoDao;
import backend.vehiculos.domain.ValidadorIdentificacionVehiculo;
import backend.vehiculos.domain.ValidadorVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class ValidacionRegistroVehiculoService {

    private static final ZoneId ZONA_NEGOCIO =
            ZoneId.of("America/Lima");

    private final CatalogosVehiculoDao catalogosDao;

    public ValidacionRegistroVehiculoService(
            CatalogosVehiculoDao catalogosDao
    ) {
        this.catalogosDao = catalogosDao;
    }

    @Transactional(readOnly = true)
    public Set<String> validar(DatosRegistroVehiculo datos) {
        Objects.requireNonNull(datos, "datos");

        var catalogos = catalogosDao.consultar();
        int anioActual = Year.now(ZONA_NEGOCIO).getValue();

        Set<String> errores = new LinkedHashSet<>(
                ValidadorVehiculo.validarDatosBasicos(datos, anioActual)
        );

        errores.addAll(
                ValidadorVehiculo.validarMotor(datos, catalogos)
        );

        if (datos.idOperacion() == null) {
            errores.add("idOperacion");
        }

        boolean usoExiste = catalogos.usos().stream()
                .anyMatch(uso -> Objects.equals(
                        uso.codigo(),
                        datos.usoVehiculoCodigo()
                ));

        if (!usoExiste) {
            errores.add("usoVehiculoCodigo");
        }

        if (!ValidadorIdentificacionVehiculo.esVinValido(
                datos.numeroVin()
        )) {
            errores.add("numeroVin");
        }

        if (!ValidadorIdentificacionVehiculo.esNumeroMotorValido(
                datos.numeroMotor()
        )) {
            errores.add("numeroMotor");
        }

        if (!ValidadorIdentificacionVehiculo.esNumeroSerieValido(
                datos.numeroSerie()
        )) {
            errores.add("numeroSerie");
        }

        return Set.copyOf(errores);
    }
}