package backend.vehiculos.domain;

import backend.vehiculos.dto.DatosCatalogosVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ValidadorVehiculo {

    private ValidadorVehiculo() {
    }

    public static boolean esAnioValido(
            Integer anioFabricacion,
            int anioActual
    ) {
        return anioFabricacion != null
                && anioFabricacion > 0
                && anioFabricacion <= anioActual;
    }

    public static boolean esKilometrajeInicialValido(
            Integer kilometrajeInicial
    ) {
        return kilometrajeInicial != null
                && kilometrajeInicial > 0;
    }

    public static Set<String> validarDatosBasicos(
            DatosRegistroVehiculo datos,
            int anioActual
    ) {
        Objects.requireNonNull(datos, "datos");

        Set<String> errores = new LinkedHashSet<>();

        if (!esTextoObligatorioValido(datos.marca(), 100)) {
            errores.add("marca");
        }

        if (!esTextoObligatorioValido(datos.modelo(), 100)) {
            errores.add("modelo");
        }

        if (!esTextoObligatorioValido(datos.color(), 80)) {
            errores.add("color");
        }

        if (!ValidadorPlaca.esValida(datos.placa())) {
            errores.add("placa");
        }

        if (!esAnioValido(datos.anioFabricacion(), anioActual)) {
            errores.add("anioFabricacion");
        }

        if (!esKilometrajeInicialValido(datos.kilometrajeInicial())) {
            errores.add("kilometrajeInicial");
        }

        return Set.copyOf(errores);
    }

    public static Set<String> validarMotor(
            DatosRegistroVehiculo datos,
            DatosCatalogosVehiculo catalogos
    ) {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(catalogos, "catalogos");

        Set<String> errores = new LinkedHashSet<>();

        var motor = catalogos.tiposMotor().stream()
                .filter(tipo -> Objects.equals(
                        tipo.idTipoMotor(),
                        datos.idTipoMotor()
                ))
                .findFirst()
                .orElse(null);

        if (motor == null) {
            errores.add("idTipoMotor");
            return Set.copyOf(errores);
        }

        if (!esEspecificacionValida(
                "OTRO".equals(motor.codigo()),
                datos.tipoMotorOtro()
        )) {
            errores.add("tipoMotorOtro");
        }

        if (motor.esElectrico()) {
            if (datos.idCombustible() != null) {
                errores.add("idCombustible");
            }

            if (datos.combustibleOtro() != null) {
                errores.add("combustibleOtro");
            }

            if (datos.idTipoAceite() != null) {
                errores.add("idTipoAceite");
            }

            if (datos.tipoAceiteOtro() != null) {
                errores.add("tipoAceiteOtro");
            }

            if (datos.numeroMotor() != null) {
                errores.add("numeroMotor");
            }

            return Set.copyOf(errores);
        }

        var combustible = catalogos.combustibles().stream()
                .filter(opcion -> Objects.equals(
                        opcion.idCombustible(),
                        datos.idCombustible()
                ))
                .findFirst()
                .orElse(null);

        if (combustible == null) {
            errores.add("idCombustible");
        } else if (!esEspecificacionValida(
                "OTRO".equals(combustible.codigo()),
                datos.combustibleOtro()
        )) {
            errores.add("combustibleOtro");
        }

        if (datos.idTipoAceite() == null) {
            if (datos.tipoAceiteOtro() != null) {
                errores.add("tipoAceiteOtro");
            }
        } else {
            var aceite = catalogos.tiposAceite().stream()
                    .filter(opcion -> Objects.equals(
                            opcion.idTipoAceite(),
                            datos.idTipoAceite()
                    ))
                    .findFirst()
                    .orElse(null);

            if (aceite == null) {
                errores.add("idTipoAceite");
            } else if (!esEspecificacionValida(
                    "OTRO".equals(aceite.codigo()),
                    datos.tipoAceiteOtro()
            )) {
                errores.add("tipoAceiteOtro");
            }
        }

        if (datos.numeroMotor() != null
                && datos.numeroMotor().codePointCount(
                0, datos.numeroMotor().length()
        ) > 80) {
            errores.add("numeroMotor");
        }

        return Set.copyOf(errores);
    }

    private static boolean esEspecificacionValida(
            boolean esOtro,
            String especificacion
    ) {
        if (esOtro) {
            return esTextoObligatorioValido(especificacion, 100);
        }

        return especificacion == null;
    }

    private static boolean esTextoObligatorioValido(
            String valor,
            int longitudMaxima
    ) {
        return valor != null
                && !valor.isBlank()
                && valor.codePointCount(0, valor.length()) <= longitudMaxima;
    }
}