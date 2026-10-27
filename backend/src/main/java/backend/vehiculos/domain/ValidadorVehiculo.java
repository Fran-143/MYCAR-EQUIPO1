package backend.vehiculos.domain;

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

    private static boolean esTextoObligatorioValido(
            String valor,
            int longitudMaxima
    ) {
        return valor != null
                && !valor.isBlank()
                && valor.codePointCount(0, valor.length()) <= longitudMaxima;
    }
}