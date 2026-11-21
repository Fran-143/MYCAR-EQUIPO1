package backend.cuentas;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CuentasRf55FiltroEfectivoTest {

    @DynamicPropertySource
    static void localOnly(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5432/mycar_mvp");
        r.add("spring.datasource.username", () -> "mycar");
        r.add("spring.datasource.password", () -> "root");
        r.add("mycar.seguridad.exigir-datasource-local", () -> "true");
        r.add("mycar.auth.jwt-secret-base64", () -> {
            try {
                var p = java.nio.file.Path.of(System.getenv("LOCALAPPDATA"), "MyCar", "desarrollo-local", "autenticacion.properties");
                for (String line : java.nio.file.Files.readAllLines(p)) {
                    if (line.startsWith("mycar.auth.jwt-secret-base64=")) {
                        return line.substring("mycar.auth.jwt-secret-base64=".length()).trim();
                    }
                }
            } catch (Exception ignored) { }
            return "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
        });
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CuentaAdminService admin;

    private long userId;

    @BeforeEach
    void setup() throws Exception {
        String u = jdbc.getDataSource().getConnection().getMetaData().getURL();
        Assumptions.assumeTrue(u.contains("127.0.0.1") || u.contains("localhost"));
        var ids = jdbc.query("SELECT id_usuario FROM mycar.usuario WHERE lower(correo)='rf.bloq.recup@mycar.test'",
                (rs, n) -> rs.getLong(1));
        Assumptions.assumeFalse(ids.isEmpty());
        userId = ids.getFirst();
        jdbc.update("""
                UPDATE mycar.usuario SET estado_cuenta='ACTIVA', tipo_bloqueo=NULL,
                  bloqueo_admin_hasta_utc=NULL, login_bloqueo_hasta_utc=NULL WHERE id_usuario=?
                """, userId);
        jdbc.update("""
                INSERT INTO mycar.restriccion_recuperacion(id_usuario, finalidad, solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc, actualizado_utc)
                VALUES (?,'RECUPERAR_CONTRASENA',0,1,?,clock_timestamp())
                ON CONFLICT (id_usuario) DO UPDATE
                  SET nivel_bloqueo=1, bloqueado_hasta_utc=EXCLUDED.bloqueado_hasta_utc, actualizado_utc=clock_timestamp()
                """, userId, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
    }

    @Test
    void filtroActivaExcluyeRecuperacionVigenteVisibleComoBloqueada() {
        Map<String, Object> activa = admin.listar("rf.bloq.recup", "ACTIVA", "USUARIO", 0, 50, true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsActiva = (List<Map<String, Object>>) activa.get("items");
        assertTrue(itemsActiva.stream().noneMatch(i -> userId == ((Number) i.get("idUsuario")).longValue()));

        Map<String, Object> bloq = admin.listar("rf.bloq.recup", "BLOQUEADA", "USUARIO", 0, 50, true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsBloq = (List<Map<String, Object>>) bloq.get("items");
        assertTrue(itemsBloq.stream().anyMatch(i -> userId == ((Number) i.get("idUsuario")).longValue()));
        Map<String, Object> hit = itemsBloq.stream()
                .filter(i -> userId == ((Number) i.get("idUsuario")).longValue()).findFirst().orElseThrow();
        assertEquals("BLOQUEADA", hit.get("estadoVisible"));
        assertEquals("ACTIVA", hit.get("estado"));
    }
}
