package backend.talleres.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TallerPilotoLocalSeedTest {
    @Test
    void normalizaPeru() {
        assertEquals("51987654321", TallerPilotoLocalSeed.normalizarPeru("987654321"));
        assertEquals("51987654321", TallerPilotoLocalSeed.normalizarPeru("+51 987 654 321"));
        assertNull(TallerPilotoLocalSeed.normalizarPeru(""));
        assertNull(TallerPilotoLocalSeed.normalizarPeru("123"));
    }
}
