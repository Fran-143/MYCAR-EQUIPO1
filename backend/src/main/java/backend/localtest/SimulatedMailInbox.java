package backend.localtest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bandeja de correos/OTP simulados. Solo operativa con mycar.local-test.enabled=true
 * y destinatarios de fixtures (@mycar.test / @invalid.mycar.local).
 */
@Service
@ConditionalOnProperty(name = "mycar.local-test.enabled", havingValue = "true")
public class SimulatedMailInbox {
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public SimulatedMailInbox(
            JdbcTemplate jdbc,
            @Value("${mycar.local-test.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public void registrar(
            String destinatario,
            String tipo,
            String asunto,
            String cuerpo,
            String otp,
            String finalidad,
            Long userId,
            Instant now,
            Instant expiraOtp) {
        if (!enabled) return;
        if (!LocalTestGuard.destinatarioPermitido(destinatario)) return;
        jdbc.update("""
                INSERT INTO mycar.correo_simulado(
                    destinatario, tipo_mensaje, asunto, cuerpo, codigo_otp, finalidad_otp,
                    id_usuario, estado, creado_utc, expira_otp_utc)
                VALUES (?,?,?,?,?,?,?,'SIMULADO',?,?)
                """,
                destinatario.strip().toLowerCase(Locale.ROOT),
                tipo,
                asunto,
                cuerpo == null ? "" : cuerpo,
                otp,
                finalidad,
                userId,
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                expiraOtp == null ? null : OffsetDateTime.ofInstant(expiraOtp, ZoneOffset.UTC));
    }

    public List<Map<String, Object>> listar(String destinatarioFiltro, int limit) {
        LocalTestGuard.requireEnabled(enabled);
        int safe = Math.min(Math.max(limit, 1), 100);
        if (destinatarioFiltro != null && !destinatarioFiltro.isBlank()) {
            String d = destinatarioFiltro.strip().toLowerCase(Locale.ROOT);
            LocalTestGuard.requireDestinatario(d);
            return jdbc.query("""
                    SELECT id_correo, destinatario, tipo_mensaje, asunto, cuerpo, codigo_otp, finalidad_otp,
                           id_usuario, estado, creado_utc, expira_otp_utc
                      FROM mycar.correo_simulado
                     WHERE lower(destinatario)=?
                     ORDER BY creado_utc DESC LIMIT ?
                    """, this::mapRow, d, safe);
        }
        return jdbc.query("""
                SELECT id_correo, destinatario, tipo_mensaje, asunto, cuerpo, codigo_otp, finalidad_otp,
                       id_usuario, estado, creado_utc, expira_otp_utc
                  FROM mycar.correo_simulado
                 WHERE lower(destinatario) LIKE '%@mycar.test'
                    OR lower(destinatario) LIKE '%@invalid.mycar.local'
                 ORDER BY creado_utc DESC LIMIT ?
                """, this::mapRow, safe);
    }

    public Map<String, Object> detalle(long id) {
        LocalTestGuard.requireEnabled(enabled);
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id_correo, destinatario, tipo_mensaje, asunto, cuerpo, codigo_otp, finalidad_otp,
                       id_usuario, estado, creado_utc, expira_otp_utc
                  FROM mycar.correo_simulado WHERE id_correo=?
                """, this::mapRow, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Mensaje no encontrado.");
        Map<String, Object> m = rows.getFirst();
        LocalTestGuard.requireDestinatario((String) m.get("destinatario"));
        return m;
    }

    public Map<String, Object> ultimoPorFinalidad(String destinatario, String finalidad) {
        LocalTestGuard.requireEnabled(enabled);
        LocalTestGuard.requireDestinatario(destinatario);
        return jdbc.query("""
                SELECT id_correo, destinatario, tipo_mensaje, asunto, cuerpo, codigo_otp, finalidad_otp,
                       id_usuario, estado, creado_utc, expira_otp_utc
                  FROM mycar.correo_simulado
                 WHERE lower(destinatario)=? AND finalidad_otp=?
                 ORDER BY creado_utc DESC LIMIT 1
                """, this::mapRow, destinatario.strip().toLowerCase(Locale.ROOT), finalidad)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay mensaje simulado para esa finalidad."));
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idCorreo", rs.getLong("id_correo"));
        m.put("destinatario", rs.getString("destinatario"));
        m.put("tipoMensaje", rs.getString("tipo_mensaje"));
        m.put("asunto", rs.getString("asunto"));
        m.put("cuerpo", rs.getString("cuerpo"));
        m.put("codigoOtp", rs.getString("codigo_otp"));
        m.put("finalidadOtp", rs.getString("finalidad_otp"));
        long uid = rs.getLong("id_usuario");
        m.put("idUsuario", rs.wasNull() ? null : uid);
        m.put("estado", rs.getString("estado"));
        m.put("creadoUtc", rs.getTimestamp("creado_utc").toInstant().toString());
        if (rs.getTimestamp("expira_otp_utc") != null) {
            m.put("expiraOtpUtc", rs.getTimestamp("expira_otp_utc").toInstant().toString());
        }
        return m;
    }
}
