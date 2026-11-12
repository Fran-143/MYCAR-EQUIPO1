package backend.recuperacion.persistence;

import backend.recuperacion.RecoveryStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class JdbcRecoveryStore implements RecoveryStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Account> ACCOUNT = (r, n) -> new Account(
            r.getLong("id_usuario"), r.getString("correo"), r.getString("estado_cuenta"),
            r.getString("password_hash"));
    private static final RowMapper<CodeRow> CODE = (r, n) -> new CodeRow(
            r.getLong("id_codigo"), r.getLong("id_usuario"), r.getString("codigo_hash"),
            r.getString("estado"), r.getTimestamp("expira_utc").toInstant(),
            r.getTimestamp("creado_utc").toInstant(), r.getInt("intentos_fallidos"));

    public JdbcRecoveryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    }

    @Override
    public Optional<Account> findByEmail(String emailNormalized) {
        return jdbc.query("""
                SELECT id_usuario, correo, estado_cuenta, password_hash FROM mycar.usuario
                 WHERE lower(btrim(correo))=?
                """, ACCOUNT, emailNormalized).stream().findFirst();
    }

    @Override
    public void invalidateActive(long userId) {
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO'
                 WHERE id_usuario=? AND estado='ACTIVO'
                """, userId);
    }

    @Override
    public long insertCode(long userId, String codeHash, Instant expiresAt, Instant now) {
        Long id = jdbc.queryForObject("""
                INSERT INTO mycar.codigo_recuperacion(id_usuario, codigo_hash, estado, expira_utc, creado_utc)
                VALUES (?, ?, 'ACTIVO', ?, ?) RETURNING id_codigo
                """, Long.class, userId, codeHash, utc(expiresAt), utc(now));
        if (id == null) throw new IllegalStateException("No se pudo registrar el código");
        return id;
    }

    @Override
    public Optional<CodeRow> lockLatestActive(long userId) {
        return jdbc.query("""
                SELECT id_codigo, id_usuario, codigo_hash, estado, expira_utc, creado_utc, intentos_fallidos
                  FROM mycar.codigo_recuperacion
                 WHERE id_usuario=? AND estado='ACTIVO'
                 ORDER BY creado_utc DESC
                 LIMIT 1
                 FOR UPDATE
                """, CODE, userId).stream().findFirst();
    }

    @Override
    public Optional<CodeRow> findLatest(long userId) {
        return jdbc.query("""
                SELECT id_codigo, id_usuario, codigo_hash, estado, expira_utc, creado_utc, intentos_fallidos
                  FROM mycar.codigo_recuperacion
                 WHERE id_usuario=?
                 ORDER BY creado_utc DESC
                 LIMIT 1
                """, CODE, userId).stream().findFirst();
    }

    @Override
    public void markConsumed(long codeId, Instant now) {
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion
                   SET estado='CONSUMIDO', consumido_utc=?
                 WHERE id_codigo=?
                """, utc(now), codeId);
    }

    @Override
    public void markInvalidated(long codeId) {
        jdbc.update("UPDATE mycar.codigo_recuperacion SET estado='INVALIDADO' WHERE id_codigo=?", codeId);
    }

    @Override
    public void incrementFailures(long codeId) {
        jdbc.update("""
                UPDATE mycar.codigo_recuperacion
                   SET intentos_fallidos=intentos_fallidos+1
                 WHERE id_codigo=?
                """, codeId);
    }

    @Override
    public int updatePassword(long userId, String passwordHash) {
        return jdbc.update("""
                UPDATE mycar.usuario SET password_hash=?
                 WHERE id_usuario=? AND estado_cuenta='ACTIVA'
                """, passwordHash, userId);
    }

    @Override
    public Optional<String> lockPasswordHash(long userId) {
        return jdbc.query("""
                SELECT password_hash FROM mycar.usuario
                 WHERE id_usuario=? AND estado_cuenta='ACTIVA'
                 FOR NO KEY UPDATE
                """, (r, n) -> r.getString(1), userId).stream().findFirst();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
