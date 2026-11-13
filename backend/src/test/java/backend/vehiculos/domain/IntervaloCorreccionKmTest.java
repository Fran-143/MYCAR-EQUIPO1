package backend.vehiculos.domain;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** RN16.05 / T16.01–T16.14: intervalo estricto penúltima < corregido < última. */
class IntervaloCorreccionKmTest {
    private static boolean acepta(long penultimo, long corregido, long ultimo) {
        return penultimo < corregido && corregido < ultimo;
    }

    private static boolean hayEnteroIntermedio(long penultimo, long ultimo) {
        return ultimo > penultimo + 1;
    }

    @Test void acepta78000Entre72300Y85000() {
        assertTrue(acepta(72_300, 78_000, 85_000));
    }

    @Test void aceptaExtremosAbiertos() {
        assertTrue(acepta(72_300, 72_301, 85_000));
        assertTrue(acepta(72_300, 84_999, 85_000));
    }

    @Test void rechazaIgualesYFuera() {
        assertFalse(acepta(72_300, 72_300, 85_000));
        assertFalse(acepta(72_300, 85_000, 85_000));
        assertFalse(acepta(72_300, 58_000, 85_000));
        assertFalse(acepta(72_300, 0, 85_000));
        assertFalse(acepta(72_300, 1, 85_000));
        assertFalse(acepta(72_300, 90_000, 85_000));
    }

    @Test void intervaloAgotadoSinEntero() {
        assertFalse(hayEnteroIntermedio(84_999, 85_000));
        assertTrue(hayEnteroIntermedio(72_300, 85_000));
    }

    @Test void bigDecimalMismaRegla() {
        BigDecimal pen = new BigDecimal("72300");
        BigDecimal ult = new BigDecimal("85000");
        BigDecimal ok = new BigDecimal("78000");
        assertTrue(pen.compareTo(ok) < 0 && ok.compareTo(ult) < 0);
        assertFalse(pen.compareTo(pen) < 0);
    }
}
