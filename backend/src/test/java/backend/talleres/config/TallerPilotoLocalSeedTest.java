package backend.talleres.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

class TallerPilotoLocalSeedTest {
    @Test
    void normalizaPeru() {
        assertEquals("51987654321", TallerPilotoLocalSeed.normalizarPeru("987654321"));
        assertEquals("51987654321", TallerPilotoLocalSeed.normalizarPeru("+51 987 654 321"));
        assertNull(TallerPilotoLocalSeed.normalizarPeru(""));
        assertNull(TallerPilotoLocalSeed.normalizarPeru("123"));
    }

    @Test
    void exigeUsuarioIdPositivoCuandoHabilitado() {
        var jdbc = mock(JdbcTemplate.class);
        var seed = new TallerPilotoLocalSeed(jdbc, "Piloto", "987654321", 0, false);
        assertThrows(IllegalStateException.class,
                () -> seed.run(new DefaultApplicationArguments()));
    }

    @Test
    void exigeWhatsappValidoCuandoHabilitado() {
        var jdbc = mock(JdbcTemplate.class);
        var seed = new TallerPilotoLocalSeed(jdbc, "Piloto", "abc", 12, false);
        assertThrows(IllegalStateException.class,
                () -> seed.run(new DefaultApplicationArguments()));
    }

    @Test
    void seedIdempotenteConContactoAutorizadoFalsePorDefecto() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("Piloto"))).thenReturn(0);
        when(jdbc.update(anyString(), anyString(), anyString(), eq(false))).thenReturn(1);
        var seed = new TallerPilotoLocalSeed(jdbc, "Piloto", "987654321", 12, false);
        seed.run(new DefaultApplicationArguments());
    }
}
