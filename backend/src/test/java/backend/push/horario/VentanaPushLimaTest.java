package backend.push.horario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class VentanaPushLimaTest {
    private static final ZoneId Z = ZoneId.of("America/Lima");

    private static VentanaPushLima en(LocalDate dia, int h, int m) {
        Instant i = dia.atTime(h, m).atZone(Z).toInstant();
        return VentanaPushLima.conReloj(Clock.fixed(i, Z));
    }

    @Test
    void ventanaDiurna() {
        LocalDate d = LocalDate.of(2026, 11, 12);
        assertFalse(en(d, 4, 59).enVentanaDespacho());
        assertFalse(en(d, 4, 59).enVentanaInmediataKm());
        assertTrue(en(d, 5, 0).enVentanaDespacho());
        assertTrue(en(d, 5, 0).enVentanaInmediataKm());
        assertTrue(en(d, 21, 59).enVentanaInmediataKm());
        assertFalse(en(d, 22, 0).enVentanaDespacho());
        assertFalse(en(d, 22, 0).enVentanaInmediataKm());
        assertFalse(en(d, 23, 30).enVentanaInmediataKm());
    }
}
