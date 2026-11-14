package backend.talleres.dao;

import backend.talleres.model.DatosTaller.*;
import backend.talleres.service.TallerService;
import backend.talleres.service.ValidadorTaller;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RF34–37 contra PostgreSQL temporal.
 * Activa con -Dmycar.rf34.tests=true y MYCAR_CATALOGO_TEST_*.
 */
@EnabledIfSystemProperty(named = "mycar.rf34.tests", matches = "true")
class Rf34TalleresPostgresTest {
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource fuente;
    private static long usuario;

    @BeforeAll static void preparar() {
        String url = requerido("MYCAR_CATALOGO_TEST_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/mycar_mvp_test_rf(?:17|18|20|21|34)_[a-f0-9]{8}")) {
            throw new IllegalStateException("RF34 solo admite BD temporal RF17/18/20/21/34 en localhost");
        }
        fuente = new DriverManagerDataSource(
                url, requerido("MYCAR_CATALOGO_TEST_USER"), requerido("MYCAR_CATALOGO_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(fuente);
        int version = jdbc.queryForObject("SHOW server_version_num", Integer.class);
        if (version < 170000 || version >= 180000) throw new IllegalStateException("Se requiere PostgreSQL 17");
        Flyway.configure().dataSource(fuente).schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).baselineOnMigrate(false).load().migrate();
        usuario = jdbc.queryForObject("""
                INSERT INTO mycar.usuario (correo,password_hash,nombre,apellido,fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
                VALUES (?,repeat('x',60),'Prueba','RF34',CURRENT_DATE - INTERVAL '30 years',clock_timestamp(),'test-v1')
                RETURNING id_usuario
                """, Long.class, "rf34-" + UUID.randomUUID() + "@example.test");
    }

    @Test void crudSoftDeleteYFavoritoUnico() {
        var svc = servicio();
        var a = svc.registrar(usuario, new RegistrarTallerRequest("Alpha", "999111222", null, null, null));
        var b = svc.registrar(usuario, new RegistrarTallerRequest("Beta", null, "beta@example.test", null, null));
        assertEquals("ACTIVO", a.estado());
        assertFalse(a.favorito());

        var fav = svc.marcarFavorito(usuario, a.idTaller());
        assertTrue(fav.favorito());
        var fav2 = svc.marcarFavorito(usuario, b.idTaller());
        assertTrue(fav2.favorito());
        assertFalse(svc.detalle(usuario, a.idTaller()).favorito());

        Integer favoritos = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.taller WHERE id_usuario=? AND favorito AND estado='ACTIVO'
                """, Integer.class, usuario);
        assertEquals(1, favoritos);

        var retirado = svc.retirar(usuario, b.idTaller(), new RetirarTallerRequest(fav2.versionFila()));
        assertEquals("INACTIVO", retirado.estado());
        assertFalse(retirado.favorito());
        assertTrue(svc.listar(usuario).talleres().stream().noneMatch(t -> t.idTaller() == b.idTaller()));

        // Legacy nombre_taller intacto en mantenimiento sin id_taller.
        // Placa oficial V7: A1B234 o ABC123 (no relajar CHECK).
        String placa = String.format("A1B%03d", (int) (usuario % 1000));
        long vehiculo = jdbc.queryForObject("""
                INSERT INTO mycar.vehiculo (id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor)
                VALUES (?,?,'M','M','PARTICULAR',2020,'Azul',
                    (SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO')) RETURNING id_vehiculo
                """, Long.class, usuario, placa);
        long mant = jdbc.queryForObject("""
                INSERT INTO mycar.mantenimiento (id_vehiculo,id_tipo_mantenimiento,fecha_servicio,km_servicio,
                    origen_km_servicio,estado,nombre_taller)
                VALUES (?,(SELECT id_tipo_mantenimiento FROM mycar.tipo_mantenimiento WHERE codigo='CAMBIO_ACEITE'),
                    CURRENT_DATE,10000,'HISTORICO_MANUAL','REALIZADO','Texto libre legacy')
                RETURNING id_mantenimiento
                """, Long.class, vehiculo);
        String legacy = jdbc.queryForObject(
                "SELECT nombre_taller FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, mant);
        assertEquals("Texto libre legacy", legacy);
        assertNull(jdbc.queryForObject(
                "SELECT id_taller FROM mycar.mantenimiento WHERE id_mantenimiento=?", Long.class, mant));

        // FK: asociar taller ACTIVO + snapshot.
        jdbc.update("UPDATE mycar.mantenimiento SET id_taller=?, nombre_taller=? WHERE id_mantenimiento=?",
                a.idTaller(), a.nombre(), mant);
        assertEquals(a.idTaller(), jdbc.queryForObject(
                "SELECT id_taller FROM mycar.mantenimiento WHERE id_mantenimiento=?", Long.class, mant));
    }

    @Test void favoritoConcurrenteDejaUnSoloFavorito() throws Exception {
        var svc = servicio();
        var t1 = svc.registrar(usuario, new RegistrarTallerRequest("C1-" + UUID.randomUUID(), null, null, null, null));
        var t2 = svc.registrar(usuario, new RegistrarTallerRequest("C2-" + UUID.randomUUID(), null, null, null, null));
        var start = new CountDownLatch(1);
        var ex1 = new AtomicReference<Throwable>();
        var ex2 = new AtomicReference<Throwable>();
        Thread h1 = new Thread(() -> {
            try { start.await(); svc.marcarFavorito(usuario, t1.idTaller()); }
            catch (Throwable t) { ex1.set(t); }
        }, "fav-1");
        Thread h2 = new Thread(() -> {
            try { start.await(); svc.marcarFavorito(usuario, t2.idTaller()); }
            catch (Throwable t) { ex2.set(t); }
        }, "fav-2");
        h1.start(); h2.start(); start.countDown();
        h1.join(15_000); h2.join(15_000);
        assertNull(ex1.get());
        assertNull(ex2.get());
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.taller WHERE id_usuario=? AND favorito AND estado='ACTIVO'
                  AND id_taller IN (?,?)
                """, Integer.class, usuario, t1.idTaller(), t2.idTaller());
        assertEquals(1, n);
    }

    private TallerService servicio() {
        var dao = new TallerJdbcDao(jdbc);
        var gestor = new DataSourceTransactionManager(fuente);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(gestor);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        var proxy = new ProxyFactory(new TallerService(dao, new ValidadorTaller(), 12L));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (TallerService) proxy.getProxy();
    }

    private static String requerido(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Falta variable " + nombre);
        return valor;
    }
}
