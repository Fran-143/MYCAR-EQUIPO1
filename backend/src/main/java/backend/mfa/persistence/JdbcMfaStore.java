package backend.mfa.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcMfaStore {
    public record Config(long userId,String estado,String secretoCifrado,String cryptoKeyId,Long ultimoPaso){
        @Override public String toString(){return "MfaConfig[REDACTED]";}
    }
    public record Desafio(UUID id,long userId,String tipo,String tokenHash,UUID installation,
                          Instant expiraUtc,int intentos,Instant bloqueoHasta,Instant consumidoUtc){
        @Override public String toString(){return "MfaDesafio[REDACTED]";}
    }

    private final JdbcTemplate jdbc;
    private static final RowMapper<Config> CONFIG=(r,n)->new Config(
            r.getLong(1),r.getString(2),r.getString(3),r.getString(4),(Long)r.getObject(5));
    private static final RowMapper<Desafio> DESAFIO=(r,n)->new Desafio(
            r.getObject(1,UUID.class),r.getLong(2),r.getString(3),r.getString(4),r.getObject(5,UUID.class),
            instant(r,6),r.getInt(7),instant(r,8),instant(r,9));

    public JdbcMfaStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Optional<Config> findConfig(long userId){
        return jdbc.query("""
                SELECT id_usuario,estado,secreto_cifrado,crypto_key_id,ultimo_paso_aceptado
                  FROM mycar.mfa_configuracion WHERE id_usuario=?
                """,CONFIG,userId).stream().findFirst();
    }
    public Optional<Config> lockConfig(long userId){
        return jdbc.query("""
                SELECT id_usuario,estado,secreto_cifrado,crypto_key_id,ultimo_paso_aceptado
                  FROM mycar.mfa_configuracion WHERE id_usuario=? FOR UPDATE
                """,CONFIG,userId).stream().findFirst();
    }
    public void upsertPendiente(long userId,String secretoCifrado,String keyId,Instant now){
        jdbc.update("""
                INSERT INTO mycar.mfa_configuracion(id_usuario,estado,secreto_cifrado,crypto_key_id,ultimo_paso_aceptado,actualizada_utc)
                VALUES(?,'PENDIENTE',?,?,NULL,?)
                ON CONFLICT (id_usuario) DO UPDATE SET
                  estado='PENDIENTE',secreto_cifrado=EXCLUDED.secreto_cifrado,crypto_key_id=EXCLUDED.crypto_key_id,
                  ultimo_paso_aceptado=NULL,activada_utc=NULL,actualizada_utc=EXCLUDED.actualizada_utc
                """,userId,secretoCifrado,keyId,utc(now));
    }
    public void activar(long userId,long paso,Instant now){
        int n=jdbc.update("""
                UPDATE mycar.mfa_configuracion
                   SET estado='ACTIVA',ultimo_paso_aceptado=?,activada_utc=?,actualizada_utc=?
                 WHERE id_usuario=? AND estado='PENDIENTE'
                """,paso,utc(now),utc(now),userId);
        if(n!=1)throw new IllegalStateException("No se pudo activar MFA");
    }
    public boolean aceptarPasoAtomico(long userId,long paso){
        return jdbc.update("""
                UPDATE mycar.mfa_configuracion
                   SET ultimo_paso_aceptado=?,actualizada_utc=clock_timestamp()
                 WHERE id_usuario=? AND estado='ACTIVA'
                   AND (ultimo_paso_aceptado IS NULL OR ultimo_paso_aceptado < ?)
                """,paso,userId,paso)==1;
    }
    public void resetParaReenroll(long userId,String secretoCifrado,String keyId,Instant now){
        jdbc.update("""
                UPDATE mycar.mfa_configuracion
                   SET estado='PENDIENTE',secreto_cifrado=?,crypto_key_id=?,ultimo_paso_aceptado=NULL,
                       activada_utc=NULL,actualizada_utc=?
                 WHERE id_usuario=?
                """,secretoCifrado,keyId,utc(now),userId);
    }
    public void invalidarRecovery(long userId){
        jdbc.update("UPDATE mycar.mfa_codigo_recuperacion SET usado_utc=clock_timestamp() WHERE id_usuario=? AND usado_utc IS NULL",userId);
    }
    public void insertRecoveryHashes(long userId,UUID loteId,List<String> hashes,Instant now){
        for(String hash:hashes){
            jdbc.update("""
                    INSERT INTO mycar.mfa_codigo_recuperacion(id_usuario,lote_id,codigo_hash,creado_utc)
                    VALUES(?,?,?,?)
                    """,userId,loteId,hash,utc(now));
        }
    }
    public Optional<Long> consumirRecovery(long userId,String hash,Instant now){
        Long id=jdbc.query("""
                UPDATE mycar.mfa_codigo_recuperacion
                   SET usado_utc=?
                 WHERE id_usuario=? AND codigo_hash=? AND usado_utc IS NULL
                RETURNING id_codigo
                """,rs->{if(rs.next())return rs.getLong(1);return null;},utc(now),userId,hash);
        return Optional.ofNullable(id);
    }
    public void crearDesafio(UUID id,long userId,String tipo,String tokenHash,UUID installation,Instant expira,Instant now){
        jdbc.update("""
                INSERT INTO mycar.mfa_desafio(id_desafio,id_usuario,tipo,token_hash,id_instalacion,expira_utc,creado_utc)
                VALUES(?,?,?,?,?,?,?)
                """,id,userId,tipo,tokenHash,installation,utc(expira),utc(now));
    }
    public Optional<Desafio> lockDesafio(UUID id){
        return jdbc.query("""
                SELECT id_desafio,id_usuario,tipo,token_hash,id_instalacion,expira_utc,intentos,bloqueo_hasta_utc,consumido_utc
                  FROM mycar.mfa_desafio WHERE id_desafio=? FOR UPDATE
                """,DESAFIO,id).stream().findFirst();
    }
    public void registrarIntentoFallido(UUID id,int intentos,Instant bloqueoHasta){
        jdbc.update("UPDATE mycar.mfa_desafio SET intentos=?,bloqueo_hasta_utc=? WHERE id_desafio=?",
                intentos,bloqueoHasta==null?null:utc(bloqueoHasta),id);
    }
    public void consumirDesafio(UUID id,Instant now){
        jdbc.update("UPDATE mycar.mfa_desafio SET consumido_utc=? WHERE id_desafio=? AND consumido_utc IS NULL",utc(now),id);
    }
    public void auditar(Long userId,UUID desafioId,String evento,String detalle){
        jdbc.update("""
                INSERT INTO mycar.mfa_auditoria(id_usuario,id_desafio,evento,detalle)
                VALUES(?,?,?,?)
                """,userId,desafioId,evento,detalle==null?null:detalle.substring(0,Math.min(240,detalle.length())));
    }
    public void marcarAdmin(long userId){
        jdbc.update("UPDATE mycar.usuario SET codigo_rol='ADMIN' WHERE id_usuario=? AND codigo_rol<>'ADMIN'",userId);
    }
    public Optional<Long> findUserIdByEmail(String email){
        return jdbc.query("SELECT id_usuario FROM mycar.usuario WHERE lower(btrim(correo))=?",
                (rs,n)->rs.getLong(1),email).stream().findFirst();
    }
    public String roleOf(long userId){
        return jdbc.queryForObject("SELECT codigo_rol FROM mycar.usuario WHERE id_usuario=?",String.class,userId);
    }
    private static Instant instant(ResultSet r,int col)throws SQLException{
        Timestamp t=r.getTimestamp(col);return t==null?null:t.toInstant();
    }
    private static OffsetDateTime utc(Instant instant){return OffsetDateTime.ofInstant(instant,ZoneOffset.UTC);}
}
