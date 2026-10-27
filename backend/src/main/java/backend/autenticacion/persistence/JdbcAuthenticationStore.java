package backend.autenticacion.persistence;

import backend.autenticacion.AuthenticationStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAuthenticationStore implements AuthenticationStore {
    private final JdbcTemplate jdbc;
    private static final String ACCOUNT="SELECT id_usuario,password_hash,estado_cuenta,login_bloqueo_hasta_utc FROM mycar.usuario ";
    private static final String SESSION="""
            SELECT s.id_sesion,s.id_usuario,s.id_instalacion,s.refresh_token_hash,s.estado_sesion,
                   s.expira_utc,s.revocada_utc,u.estado_cuenta
              FROM mycar.sesion s JOIN mycar.usuario u ON u.id_usuario=s.id_usuario
            """;
    private static final RowMapper<Account> ACCOUNT_MAPPER=(r,n)->new Account(r.getLong(1),r.getString(2),r.getString(3),instant(r,4));
    private static final RowMapper<Session> SESSION_MAPPER=(r,n)->new Session(r.getLong(1),r.getLong(2),r.getObject(3,UUID.class),
            r.getString(4),r.getString(5),instant(r,6),instant(r,7),r.getString(8));
    public JdbcAuthenticationStore(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Instant now(){return jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();}
    public Optional<Account> lockAccount(String email){return jdbc.query(ACCOUNT+"WHERE lower(btrim(correo))=? FOR NO KEY UPDATE",ACCOUNT_MAPPER,email).stream().findFirst();}
    public Optional<Account> lockAccount(long userId){return jdbc.query(ACCOUNT+"WHERE id_usuario=? FOR NO KEY UPDATE",ACCOUNT_MAPPER,userId).stream().findFirst();}
    public void recordFailure(long userId){jdbc.query("SELECT mycar.registrar_fallo_login(?)",rs -> (Object)null,userId);}
    public void resetFailures(long userId){jdbc.query("SELECT mycar.reiniciar_fallos_login(?)",rs -> (Object)null,userId);}

    public void replaceInstallation(long userId,UUID installation,Instant now){
        jdbc.update("""
                UPDATE mycar.sesion SET estado_sesion=CASE WHEN expira_utc<=? THEN 'EXPIRADA' ELSE 'REVOCADA' END,
                  revocada_utc=CASE WHEN expira_utc<=? THEN revocada_utc ELSE ? END
                WHERE id_usuario=? AND id_instalacion=? AND estado_sesion='VIGENTE'
                """,utc(now),utc(now),utc(now),userId,installation);
    }
    public long createSession(long userId,UUID installation,String hash,Instant expiresAt,Instant now){
        Long id=jdbc.queryForObject("""
                INSERT INTO mycar.sesion(id_usuario,id_instalacion,refresh_token_hash,expira_utc,ultimo_acceso_utc)
                VALUES(?,?,?,?,?) RETURNING id_sesion
                """,Long.class,userId,installation,hash,utc(expiresAt),utc(now));
        if(id==null)throw new IllegalStateException("Session persistence failed");
        return id;
    }
    public Optional<Session> findByRefreshHash(String hash){return jdbc.query(SESSION+" WHERE s.refresh_token_hash=?",SESSION_MAPPER,hash).stream().findFirst();}
    public Optional<Session> lockSession(long id){return jdbc.query(SESSION+" WHERE s.id_sesion=? FOR UPDATE OF s",SESSION_MAPPER,id).stream().findFirst();}
    public Optional<Session> findSession(long id){return jdbc.query(SESSION+" WHERE s.id_sesion=?",SESSION_MAPPER,id).stream().findFirst();}
    public void rotateRefresh(long id,String hash,Instant now){jdbc.update("UPDATE mycar.sesion SET refresh_token_hash=?,ultimo_acceso_utc=? WHERE id_sesion=?",hash,utc(now),id);}
    private static Instant instant(ResultSet r,int col)throws SQLException {Timestamp t=r.getTimestamp(col);return t==null?null:t.toInstant();}
    private static OffsetDateTime utc(Instant instant){return OffsetDateTime.ofInstant(instant,ZoneOffset.UTC);}
}
