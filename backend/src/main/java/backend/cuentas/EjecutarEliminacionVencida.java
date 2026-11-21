package backend.cuentas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EjecutarEliminacionVencida {
    private static final Logger log = LoggerFactory.getLogger(EjecutarEliminacionVencida.class);

    private final JdbcTemplate jdbc;
    private final EliminacionCuentaService eliminacion;

    public EjecutarEliminacionVencida(JdbcTemplate jdbc, EliminacionCuentaService eliminacion) {
        this.jdbc = jdbc;
        this.eliminacion = eliminacion;
    }

    public Map<String, Object> ejecutar(Instant now, int limite) {
        int lim = Math.min(Math.max(limite, 1), 100);
        List<Long> ids = jdbc.query("""
                SELECT s.id_usuario
                  FROM mycar.solicitud_eliminacion s
                  JOIN mycar.usuario u ON u.id_usuario=s.id_usuario
                 WHERE s.estado='VIGENTE'
                   AND u.estado_cuenta='PENDIENTE_ELIMINACION'
                   AND s.vence_utc <= ?
                 ORDER BY s.vence_utc ASC
                 LIMIT ?
                """, (r, n) -> r.getLong(1),
                java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC), lim);
        List<Long> ok = new ArrayList<>();
        for (Long id : ids) {
            try {
                if (eliminacion.ejecutarSupresion(id, now)) ok.add(id);
            } catch (Exception ex) {
                log.warn("supresion_fallo userId={} err={}", id, ex.toString());
            }
        }
        return Map.of("procesadas", ok.size(), "ids", ok, "nota",
                "Servicio local idempotente. No implica cron productivo garantizado.");
    }
}
