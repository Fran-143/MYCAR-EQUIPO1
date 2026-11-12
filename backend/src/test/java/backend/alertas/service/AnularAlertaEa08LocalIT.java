package backend.alertas.service;

import backend.alertas.api.dto.ContratoRf22;
import backend.alertas.dao.AlertasJdbcDao;
import backend.alertas.dao.OperacionAlertaJdbcDao;
import backend.mantenimiento.service.RegistroMantenimientoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * RF24-EA08 contra Postgres local de integración (mycar_mvp).
 * Activa: -Dmycar.rf24.ea08.local=true
 * Lee JDBC de variables de entorno del .env / defaults locales (no imprime secretos).
 */
@EnabledIfSystemProperty(named = "mycar.rf24.ea08.local", matches = "true")
class AnularAlertaEa08LocalIT {

    @Test
    void anulaConMotivoEIdempotenciaPorIdOperacion() {
        String url = env("MYCAR_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/mycar_mvp");
        String user = env("POSTGRES_USER", env("MYCAR_DB_USER", "mycar"));
        String pass = env("POSTGRES_PASSWORD", env("MYCAR_DB_PASSWORD", "mycar"));
        var ds = new DriverManagerDataSource(url, user, pass);
        var jdbc = new JdbcTemplate(ds);
        var txm = new DataSourceTransactionManager(ds);

        Long idAlerta = jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, v.id_usuario
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                WHERE a.estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                  AND a.categoria = 'MANTENIMIENTO'
                ORDER BY a.id_alerta
                LIMIT 1
                """, (rs, n) -> rs.getLong(1)).stream().findFirst().orElse(null);
        assertNotNull(idAlerta, "Se requiere al menos una alerta anulable en mycar_mvp");
        long vehiculo = jdbc.queryForObject(
                "SELECT id_vehiculo FROM mycar.alerta WHERE id_alerta=?", Long.class, idAlerta);
        long usuario = jdbc.queryForObject(
                "SELECT id_usuario FROM mycar.vehiculo WHERE id_vehiculo=?", Long.class, vehiculo);
        Long idRef = jdbc.queryForObject(
                "SELECT id_mantenimiento_referencia FROM mycar.alerta WHERE id_alerta=?", Long.class, idAlerta);
        String estadoRefAntes = idRef == null ? null : jdbc.queryForObject(
                "SELECT estado FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, idRef);

        var alertas = new AlertasJdbcDao(jdbc);
        var operaciones = new OperacionAlertaJdbcDao(jdbc, new ObjectMapper());
        var servicio = transactional(new AtenderAlertaService(
                alertas, mock(RegistroMantenimientoService.class), operaciones, new ObjectMapper()), txm);

        UUID op = UUID.randomUUID();
        String motivo = "MotivoEA08Local";
        var r1 = servicio.anular(usuario, vehiculo, idAlerta, new ContratoRf22.AnularRequest(op, motivo));
        assertEquals("ANULADA", r1.estado());
        assertEquals(op, r1.idOperacion());

        String estado = jdbc.queryForObject("SELECT estado FROM mycar.alerta WHERE id_alerta=?", String.class, idAlerta);
        assertEquals("ANULADA", estado);
        String motivoHist = jdbc.queryForObject("""
                SELECT motivo FROM mycar.historial_alerta
                WHERE id_alerta=? AND estado_nuevo='ANULADA'
                ORDER BY version_destino DESC LIMIT 1
                """, String.class, idAlerta);
        assertEquals(motivo, motivoHist);
        Integer ops = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.operacion_idempotente
                WHERE id_operacion=? AND tipo_operacion='ANULAR_ALERTA' AND estado='CONFIRMADA'
                """, Integer.class, op);
        assertEquals(1, ops);

        if (idRef != null) {
            String estadoRefDespues = jdbc.queryForObject(
                    "SELECT estado FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, idRef);
            assertEquals(estadoRefAntes, estadoRefDespues);
        }

        // Reintento misma operación: no duplica historial
        Integer histAntes = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=? AND estado_nuevo='ANULADA'",
                Integer.class, idAlerta);
        var r2 = servicio.anular(usuario, vehiculo, idAlerta, new ContratoRf22.AnularRequest(op, motivo));
        assertEquals("ANULADA", r2.estado());
        Integer histDespues = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=? AND estado_nuevo='ANULADA'",
                Integer.class, idAlerta);
        assertEquals(histAntes, histDespues);

        // Nueva operación sobre ya ANULADA: no reescribe
        UUID op2 = UUID.randomUUID();
        var r3 = servicio.anular(usuario, vehiculo, idAlerta, new ContratoRf22.AnularRequest(op2, "OtroMotivo"));
        assertEquals("ANULADA", r3.estado());
        Integer histFinal = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=? AND estado_nuevo='ANULADA'",
                Integer.class, idAlerta);
        assertEquals(histAntes, histFinal);
    }

    private static AtenderAlertaService transactional(AtenderAlertaService target, DataSourceTransactionManager txm) {
        ProxyFactory pf = new ProxyFactory(target);
        pf.setProxyTargetClass(true);
        pf.addAdvice(new TransactionInterceptor(txm, new AnnotationTransactionAttributeSource()));
        return (AtenderAlertaService) pf.getProxy();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
