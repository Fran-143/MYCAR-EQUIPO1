package backend.alertas.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF22 P1: sin tipo / OTRO sin texto no consulta la BD. */
class AlertasJdbcDaoReferenciasTest {

    @Test
    void sinCodigoTipoNoConsulta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var dao = new AlertasJdbcDao(jdbc);
        assertTrue(dao.referenciasRealizadas(1L, null, null).isEmpty());
        assertTrue(dao.referenciasRealizadas(1L, "  ", null).isEmpty());
        verifyNoInteractions(jdbc);
    }

    @Test
    void otroSinServicioNoConsulta() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var dao = new AlertasJdbcDao(jdbc);
        assertTrue(dao.referenciasRealizadas(1L, "OTRO", null).isEmpty());
        assertTrue(dao.referenciasRealizadas(1L, "OTRO", "  ").isEmpty());
        verifyNoInteractions(jdbc);
    }
}
