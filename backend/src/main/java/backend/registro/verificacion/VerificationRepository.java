package backend.registro.verificacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Explicitly constructed, not discovered by Spring. All SQL is parameterized. */
public final class VerificationRepository {
    final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Supplier<Instant> time;
    public VerificationRepository(DataSource ds) { this(ds, new DataSourceTransactionManager(ds), null); }
    public VerificationRepository(DataSource ds, PlatformTransactionManager txManager) {
        this(ds, txManager, null);
    }
    // Package-private deterministic time seam, used only by PostgreSQL boundary tests.
    VerificationRepository(DataSource ds, PlatformTransactionManager txManager, Supplier<Instant> testTime) {
        jdbc=new JdbcTemplate(ds);
        transactions=new TransactionTemplate(txManager == null ? new DataSourceTransactionManager(ds) : txManager);
        time=testTime==null?()->jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant():testTime;
    }
    <T>T tx(Supplier<T> body) { return transactions.execute(status->body.get()); }
    Instant now() { return time.get(); }
    static Timestamp at(Instant i) { return i==null?null:Timestamp.from(i); }
    int update(String sql,Object... values) {return jdbc.update(sql,values);}
    Row one(String sql,Object... values) {
        var rows=jdbc.queryForList(sql,values);return rows.isEmpty()?null:new Row(rows.getFirst());
    }
    List<Row> rows(String sql,Object... values) {return jdbc.queryForList(sql,values).stream().map(Row::new).toList();}
    Row operation(UUID id) {return one("SELECT * FROM mycar.operacion_verificacion WHERE id_operacion=?",id);}
    Row emission(UUID id) {return one("SELECT * FROM mycar.emision_codigo WHERE id_emision=?",id);}
    Row current(UUID op) {return one("SELECT * FROM mycar.emision_codigo WHERE id_operacion=? AND estado_codigo IN ('RESERVADO','ACTIVO')",op);}
    Row receipt(UUID op,UUID request) {return one("SELECT * FROM mycar.intento_verificacion WHERE id_operacion=? AND id_solicitud=?",op,request);}
    Row lockOrigin(UUID id) {return one("SELECT * FROM mycar.control_verificacion WHERE id_control=? AND tipo='ORIGEN' AND finalidad='REGISTRO' FOR UPDATE",id);}
    void lockEmails(Collection<UUID> ids) {
        // PostgreSQL UUID ordering is also the order of the canonical textual encoding.
        ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).forEach(id->one("SELECT id_control FROM mycar.control_verificacion WHERE id_control=? AND tipo='CORREO' FOR UPDATE",id));
    }
    Row lockOperation(UUID id) {return one("SELECT * FROM mycar.operacion_verificacion WHERE id_operacion=? FOR UPDATE",id);}
    Row email(UUID id) {return one("SELECT * FROM mycar.control_verificacion WHERE id_control=? AND tipo='CORREO'",id);}
    UUID ensureEmail(String email,byte[] subject,Instant now) {
        update("""
            INSERT INTO mycar.control_verificacion(id_control,finalidad,tipo,clave_sujeto,correo_normalizado,creado_utc,ultimo_uso_utc)
            VALUES (?,'REGISTRO','CORREO',?,?,?,?) ON CONFLICT DO NOTHING
            """,UUID.randomUUID(),subject,email,at(now),at(now));
        return one("SELECT id_control FROM mycar.control_verificacion WHERE finalidad='REGISTRO' AND tipo='CORREO' AND correo_normalizado=?",email).id("id_control");
    }
    void close(Row e,String state,String reason,Instant now) {
        if(e==null)return;
        update("""
            UPDATE mycar.emision_codigo SET estado_codigo=?,cerrada_utc=?,error_categoria=?,
             estado_cupo=CASE WHEN primer_despacho_utc IS NULL THEN 'LIBERADO' ELSE estado_cupo END,
             verificador=NULL,clave_verificador_version=NULL,payload_cifrado=NULL,nonce=NULL,clave_cifrado_id=NULL,
             lease_id=NULL,lease_hasta_utc=NULL
            WHERE id_emision=? AND estado_codigo IN ('RESERVADO','ACTIVO')
            """,state,at(now),reason,e.id("id_emision"));
    }
    static final class Row {
        private final Map<String,Object> values;
        Row(Map<String,Object> values){this.values=values;}
        String text(String k){return (String)values.get(k);}
        UUID id(String k){return (UUID)values.get(k);}
        long number(String k){Object n=values.get(k);return n==null?0:((Number)n).longValue();}
        Instant instant(String k){Object v=values.get(k);return v==null?null:((Timestamp)v).toInstant();}
        byte[] bytes(String k){return (byte[])values.get(k);}
        @Override public String toString(){return "VerificationRow[REDACTED]";}
    }
}
