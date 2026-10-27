package backend.vehiculos.domain;

import java.util.regex.Pattern;

public final class ValidadorIdentificacionVehiculo {

    private static final Pattern FORMATO_VIN =
            Pattern.compile("[A-HJ-NPR-Z0-9]{17}");

    private static final Pattern FORMATO_MOTOR =
            Pattern.compile("[A-Z0-9 -]{5,15}");

    private static final Pattern FORMATO_SERIE =
            Pattern.compile("[A-Z0-9]{6,80}");

    private ValidadorIdentificacionVehiculo() {
    }

    public static boolean esVinValido(String vin) {
        return vin == null
                || FORMATO_VIN.matcher(vin).matches();
    }

    public static boolean esNumeroMotorValido(String numeroMotor) {
        return numeroMotor == null
                || (!numeroMotor.isBlank()
                && FORMATO_MOTOR.matcher(numeroMotor).matches());
    }

    public static boolean esNumeroSerieValido(String numeroSerie) {
        return numeroSerie == null
                || FORMATO_SERIE.matcher(numeroSerie).matches();
    }
}