package backend.historial.dao;

import backend.historial.model.AnulacionBloqueadaPorAlerta;
import backend.historial.model.DatosHistorial.*;
import backend.historial.model.MantenimientoNoDisponible;
import backend.historial.service.HistorialMantenimientoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RF20 PATCH + RF21 anulación contra PostgreSQL temporal.
 * Activa con -Dmycar.rf21.tests=true y MYCAR_CATALOGO_TEST_*.
 */
@EnabledIfSystemProperty(named = "mycar.rf21.tests", matches = "true")
class Rf20Rf21HistorialPostgresTest {
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource fuente;
    private static long usuario, vehiculo, editable, conAlerta, yaAnulado;

    @BeforeAll static void preparar() {
        String url = requerido("MYCAR_CATALOGO_TEST_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/mycar_mvp_test_rf(?:17|18|20|21)_[a-f0-9]{8}")) {
            throw new IllegalStateException("RF20/21 solo admite BD temporal RF17/18/20/21 en localhost");
        }
        fuente = new DriverManagerDataSource(
                url, requerido("MYCAR_CATALOGO_TEST_USER"), requerido("MYCAR_CATALOGO_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(fuente);
        int version = jdbc.queryForObject("SHOW server_version_num", Integer.class);
        if (version < 170000 || version >= 180000) throw new IllegalStateException("Se requiere PostgreSQL 17");
        Flyway.configure().dataSource(fuente).schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).baselineOnMigrate(false).load().migrate();
        new TransactionTemplate(new DataSourceTransactionManager(fuente)).executeWithoutResult(s -> {
            usuario = cuenta();
            vehiculo = vehiculo(usuario, "X8X" + String.format("%03d", usuario % 1000));
            jdbc.update("UPDATE mycar.vehiculo SET es_principal=true WHERE id_vehiculo=?", vehiculo);
            var lima = ZoneId.of("America/Lima");
            var hoy = LocalDate.now(lima);
            UUID opInicial = UUID.randomUUID();
            UUID opActual = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO mycar.operacion_idempotente (id_operacion,id_usuario,tipo_operacion,hash_solicitud)
                    VALUES (?,?,'REGISTRAR_VEHICULO',?), (?,?,'ACTUALIZAR_KM',?)
                    """, opInicial, usuario, "a".repeat(64), opActual, usuario, "b".repeat(64));
            // Lectura vigente < km_objetivo de alerta (trigger exige objetivo > vigente).
            jdbc.update("""
                    INSERT INTO mycar.lectura_kilometraje (id_vehiculo,valor_km,origen,fecha_lectura_utc,id_operacion)
                    VALUES (?,20000,'INICIAL',?,?), (?,44000,'ACTUALIZACION',?,?)
                    """, vehiculo, hoy.minusDays(10).atTime(12, 0).atZone(lima).toOffsetDateTime(), opInicial,
                    vehiculo, hoy.minusDays(1).atTime(12, 0).atZone(lima).toOffsetDateTime(), opActual);
            editable = mantenimiento(vehiculo, "CAMBIO_ACEITE", hoy.minusDays(10), 40000, "REALIZADO",
                    new BigDecimal("80.00"), "Taller Viejo", "Obs inicial");
            long idRepuesto = jdbc.queryForObject("""
                    INSERT INTO mycar.repuesto (id_usuario, nombre, marca)
                    VALUES (?, 'Filtro aceite', 'Mann') RETURNING id_repuesto
                    """, Long.class, usuario);
            jdbc.update("""
                    INSERT INTO mycar.mantenimiento_repuesto
                        (id_mantenimiento, id_repuesto, nombre_snapshot, cantidad, precio_unitario)
                    VALUES (?, ?, 'Filtro aceite', 1, 25.00)
                    """, editable, idRepuesto);
            // REALIZADO del tipo MANTENIMIENTO_GENERAL: referencia de alerta DESDE_MANTENIMIENTO.
            conAlerta = mantenimiento(vehiculo, "MANTENIMIENTO_GENERAL", hoy.minusDays(2), 45000, "REALIZADO",
                    new BigDecimal("120.00"), "Taller B", null);
            yaAnulado = mantenimiento(vehiculo, "CAMBIO_ACEITE", hoy.minusDays(20), 35000, "ANULADO",
                    BigDecimal.ZERO, null, null);
            // km_objetivo debe superar lectura vigente (44000).
            String firma = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
            jdbc.update("""
                    INSERT INTO mycar.alerta (
                        id_vehiculo, id_tipo_mantenimiento, criterio, origen_calculo,
                        km_base, intervalo_km, km_objetivo, estado, firma_equivalencia,
                        id_mantenimiento_referencia)
                    SELECT ?, id_tipo_mantenimiento, 'POR_KILOMETRAJE', 'DESDE_MANTENIMIENTO',
                           45000, 5000, 50000, 'PROGRAMADA', ?, ?
                    FROM mycar.tipo_mantenimiento WHERE codigo = 'MANTENIMIENTO_GENERAL'
                    """, vehiculo, firma, conAlerta);
        });
    }

    @Test void rf19_detalleIncluyeMarcaRepuesto() {
        var d = servicio().detalle(usuario, vehiculo, editable);
        assertFalse(d.repuestos().isEmpty());
        assertEquals("Filtro aceite", d.repuestos().getFirst().nombre());
        assertEquals("Mann", d.repuestos().getFirst().marca());
        assertTrue(d.editable());
        assertTrue(d.anulable());
        assertFalse(d.bloqueadoPorAlertaActiva());
    }

    @Test void rf20_patchSoloCostoObsTallerYNormalizaSinTaller() {
        long id = new TransactionTemplate(new DataSourceTransactionManager(fuente)).execute(s ->
                mantenimiento(vehiculo, "CAMBIO_ACEITE",
                        LocalDate.now(ZoneId.of("America/Lima")).minusDays(8),
                        41000, "REALIZADO", new BigDecimal("80.00"), "Taller Viejo", "Obs inicial"));
        var svc = servicio();
        var r = svc.modificar(usuario, vehiculo, id,
                new ModificarMantenimientoRequest(new BigDecimal("99.50"), "Obs RF20", "Sin taller"));
        assertEquals(0, new BigDecimal("99.50").compareTo(r.costoTotal()));
        assertEquals("Obs RF20", r.observaciones());
        assertNull(r.taller());
        assertEquals("Obs RF20", jdbc.queryForObject(
                "SELECT descripcion FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, id));
        assertNull(jdbc.queryForObject(
                "SELECT nombre_taller FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, id));
        assertEquals("CAMBIO_ACEITE", r.codigoTipo());
        assertEquals(41000, r.kmServicio());
    }

    @Test void rf20_rechazaAnuladoYMantienePayloadSoloAutorizado() {
        assertThrows(MantenimientoNoDisponible.class, () ->
                servicio().modificar(usuario, vehiculo, yaAnulado,
                        new ModificarMantenimientoRequest(BigDecimal.ONE, "x", "Taller")));
    }

    @Test void rf21_anularOkSinAlerta() {
        long lecturasAntes = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", Long.class, vehiculo);
        long id = new TransactionTemplate(new DataSourceTransactionManager(fuente)).execute(s ->
                mantenimiento(vehiculo, "OTRO", LocalDate.now(ZoneId.of("America/Lima")).minusDays(1),
                        46000, "REALIZADO", new BigDecimal("10"), "Taller Hist", "Obs hist"));
        long idRep = jdbc.queryForObject("""
                INSERT INTO mycar.repuesto (id_usuario, nombre, marca)
                VALUES (?, 'Pastillas', 'Brembo') RETURNING id_repuesto
                """, Long.class, usuario);
        jdbc.update("""
                INSERT INTO mycar.mantenimiento_repuesto
                    (id_mantenimiento, id_repuesto, nombre_snapshot, cantidad, precio_unitario)
                VALUES (?, ?, 'Pastillas', 2, 40.00)
                """, id, idRep);
        var r = servicio().anular(usuario, vehiculo, id);
        assertEquals("ANULADO", r.estado());
        assertNotNull(r.anuladoUtc());
        assertEquals("ANULADO", jdbc.queryForObject(
                "SELECT estado FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, id));
        assertNotNull(jdbc.queryForObject(
                "SELECT anulado_utc FROM mycar.mantenimiento WHERE id_mantenimiento=?", Object.class, id));
        assertEquals(46000, jdbc.queryForObject(
                "SELECT km_servicio FROM mycar.mantenimiento WHERE id_mantenimiento=?", Integer.class, id));
        assertEquals("Taller Hist", jdbc.queryForObject(
                "SELECT nombre_taller FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, id));
        assertEquals(2, jdbc.queryForObject(
                "SELECT cantidad FROM mycar.mantenimiento_repuesto WHERE id_mantenimiento=?", Integer.class, id));
        assertEquals(lecturasAntes, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", Long.class, vehiculo));
        var detalle = servicio().detalle(usuario, vehiculo, id);
        assertEquals("ANULADO", detalle.estado());
        assertNotNull(detalle.anuladoUtc());
        assertFalse(detalle.editable());
        assertFalse(detalle.anulable());
        assertEquals(1, detalle.repuestos().size());
    }

    @Test void rf21_bloqueaConAlertaActiva() {
        var ex = assertThrows(AnulacionBloqueadaPorAlerta.class, () ->
                servicio().anular(usuario, vehiculo, conAlerta));
        assertTrue(ex.idAlerta() > 0);
        assertEquals("REALIZADO", jdbc.queryForObject(
                "SELECT estado FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, conAlerta));
        assertNull(jdbc.queryForObject(
                "SELECT anulado_utc FROM mycar.mantenimiento WHERE id_mantenimiento=?", Object.class, conAlerta));
        var d = servicio().detalle(usuario, vehiculo, conAlerta);
        assertTrue(d.bloqueadoPorAlertaActiva());
        assertFalse(d.anulable());
        assertTrue(d.editable()); // RF20 editable aunque haya alerta
    }

    @Test void rf21_dobleAnulacionRechazada() {
        assertThrows(MantenimientoNoDisponible.class, () ->
                servicio().anular(usuario, vehiculo, yaAnulado));
    }

    /** RF21-EA02: referencias REALIZADA/ANULADA no bloquean. */
    @Test void rf21_ea02_alertasHistoricasCerradasPermitenAnular() {
        var hoy = LocalDate.now(ZoneId.of("America/Lima"));
        long id = new TransactionTemplate(new DataSourceTransactionManager(fuente)).execute(s ->
                mantenimiento(vehiculo, "CAMBIO_FILTROS", hoy.minusDays(4), 42000, "REALIZADO",
                        new BigDecimal("55"), "Taller EA02", null));
        String firma = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
        long idAlerta = jdbc.queryForObject("""
                INSERT INTO mycar.alerta (
                    id_vehiculo, id_tipo_mantenimiento, criterio, origen_calculo,
                    km_base, intervalo_km, km_objetivo, estado, firma_equivalencia,
                    id_mantenimiento_referencia)
                SELECT ?, id_tipo_mantenimiento, 'POR_KILOMETRAJE', 'DESDE_MANTENIMIENTO',
                       42000, 5000, 47000, 'PROGRAMADA', ?, ?
                FROM mycar.tipo_mantenimiento WHERE codigo = 'CAMBIO_FILTROS'
                RETURNING id_alerta
                """, Long.class, vehiculo, firma, id);
        // Cerrar sin reabrir: ANULADA con evidencia de fecha (CHECK de esquema).
        jdbc.update("""
                UPDATE mycar.alerta
                SET estado = 'ANULADA', version_fila = version_fila + 1,
                    fecha_vencida_verificable = ?, fecha_estado_utc = clock_timestamp()
                WHERE id_alerta = ?
                """, hoy, idAlerta);
        assertEquals("ANULADA", jdbc.queryForObject(
                "SELECT estado FROM mycar.alerta WHERE id_alerta=?", String.class, idAlerta));
        var r = servicio().anular(usuario, vehiculo, id);
        assertEquals("ANULADO", r.estado());
        assertEquals("ANULADA", jdbc.queryForObject(
                "SELECT estado FROM mycar.alerta WHERE id_alerta=?", String.class, idAlerta));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.alerta WHERE id_alerta=? AND id_mantenimiento_referencia=?",
                Integer.class, idAlerta, id));
    }

    /**
     * RF21-EA04: carrera anular vs crear alerta activa.
     * Invariante: nunca queda mantenimiento ANULADO con alerta activa referenciándolo.
     * (FOR UPDATE serializa el FK; el perdedor falla o el trigger V19 rechaza.)
     */
    @Test void rf21_ea04_alertaConcurrenteImpideAnulacionIncompatible() throws Exception {
        var hoy = LocalDate.now(ZoneId.of("America/Lima"));
        long id = new TransactionTemplate(new DataSourceTransactionManager(fuente)).execute(s ->
                mantenimiento(vehiculo, "REVISION_FRENOS", hoy.minusDays(3), 43000, "REALIZADO",
                        new BigDecimal("70"), "Taller EA04", null));
        var start = new java.util.concurrent.CountDownLatch(1);
        var anularEx = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var alertaEx = new java.util.concurrent.atomic.AtomicReference<Throwable>();

        Thread anular = new Thread(() -> {
            try {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                Thread.sleep(50); // ventana para que la alerta intente INSERT
                servicio().anular(usuario, vehiculo, id);
            } catch (Throwable t) {
                anularEx.set(t);
            }
        }, "rf21-ea04-anular");

        Thread crearAlerta = new Thread(() -> {
            try {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                String firma = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
                jdbc.update("""
                        INSERT INTO mycar.alerta (
                            id_vehiculo, id_tipo_mantenimiento, criterio, origen_calculo,
                            km_base, intervalo_km, km_objetivo, estado, firma_equivalencia,
                            id_mantenimiento_referencia)
                        SELECT ?, id_tipo_mantenimiento, 'POR_KILOMETRAJE', 'DESDE_MANTENIMIENTO',
                               43000, 5000, 48000, 'PROGRAMADA', ?, ?
                        FROM mycar.tipo_mantenimiento WHERE codigo = 'REVISION_FRENOS'
                        """, vehiculo, firma, id);
            } catch (Throwable t) {
                alertaEx.set(t);
            }
        }, "rf21-ea04-alerta");

        anular.start();
        crearAlerta.start();
        start.countDown();
        anular.join(20_000);
        crearAlerta.join(20_000);

        String estadoFinal = jdbc.queryForObject(
                "SELECT estado FROM mycar.mantenimiento WHERE id_mantenimiento=?", String.class, id);
        int alertasActivas = jdbc.queryForObject("""
                SELECT count(*) FROM mycar.alerta
                WHERE id_mantenimiento_referencia = ?
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                """, Integer.class, id);
        // Invariante RF21↔RF22: nunca ANULADO + alerta activa.
        assertFalse("ANULADO".equals(estadoFinal) && alertasActivas > 0,
                "Inconsistencia EA04: ANULADO con alerta activa");
        assertTrue(
                ("ANULADO".equals(estadoFinal) && alertasActivas == 0 && anularEx.get() == null)
                        || ("REALIZADO".equals(estadoFinal) && alertasActivas >= 1 && anularEx.get() != null)
                        || ("REALIZADO".equals(estadoFinal) && alertasActivas == 0 && alertaEx.get() != null),
                "Estados finales inesperados estado=" + estadoFinal
                        + " alertas=" + alertasActivas
                        + " anularEx=" + anularEx.get()
                        + " alertaEx=" + alertaEx.get());
    }

    private HistorialMantenimientoService servicio() {
        var dao = new HistorialMantenimientoJdbcDao(jdbc);
        var gestor = new DataSourceTransactionManager(fuente);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(gestor);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        var proxy = new ProxyFactory(new HistorialMantenimientoService(dao, org.mockito.Mockito.mock(backend.talleres.service.TallerService.class)));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (HistorialMantenimientoService) proxy.getProxy();
    }

    private static long cuenta() {
        return jdbc.queryForObject("""
                INSERT INTO mycar.usuario (correo,password_hash,nombre,apellido,fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
                VALUES (?,repeat('x',60),'Prueba','RF21',CURRENT_DATE - INTERVAL '30 years',clock_timestamp(),'test-v1')
                RETURNING id_usuario
                """, Long.class, "rf21-" + UUID.randomUUID() + "@example.test");
    }

    private static long vehiculo(long usuario, String placa) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.vehiculo (id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor)
                VALUES (?,?,'Marca','Modelo','PARTICULAR',2020,'Azul',
                    (SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO')) RETURNING id_vehiculo
                """, Long.class, usuario, placa);
    }

    private static long mantenimiento(long vehiculo, String tipo, LocalDate fecha, int km, String estado,
                                      Object costo, String taller, String obs) {
        String otro = "OTRO".equals(tipo) ? "Servicio histórico RF21" : null;
        return jdbc.queryForObject("""
                INSERT INTO mycar.mantenimiento (id_vehiculo,id_tipo_mantenimiento,fecha_servicio,km_servicio,
                    origen_km_servicio,estado,costo_total,nombre_taller,descripcion,servicio_otro)
                VALUES (?,(SELECT id_tipo_mantenimiento FROM mycar.tipo_mantenimiento WHERE codigo=?),?,?,
                    'HISTORICO_MANUAL',?,?,?,?,?) RETURNING id_mantenimiento
                """, Long.class, vehiculo, tipo, fecha, km, estado, costo, taller, obs, otro);
    }

    private static String requerido(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Falta variable " + nombre);
        return valor;
    }
}
