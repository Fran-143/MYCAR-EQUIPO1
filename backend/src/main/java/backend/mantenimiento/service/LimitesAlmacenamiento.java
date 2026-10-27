package backend.mantenimiento.service;

import java.math.BigDecimal;

final class LimitesAlmacenamiento {
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("9999999999.99");
    private LimitesAlmacenamiento() { }
    static void texto(String campo, String valor, int maximo) {
        if (valor != null && valor.codePointCount(0, valor.length()) > maximo) {
            throw new IllegalArgumentException(campo + ": supera " + maximo + " caracteres");
        }
    }
    static void importe(String campo, BigDecimal valor) {
        if (valor != null && (valor.signum() < 0
                || valor.compareTo(MAX_IMPORTE) > 0
                || valor.stripTrailingZeros().scale() > 2)) {
            throw new IllegalArgumentException(campo
                    + ": debe ser no negativo, de hasta 10 enteros y 2 decimales");
        }
    }
}
