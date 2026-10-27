package backend.mantenimiento.service;

import backend.mantenimiento.dao.LecturaKmMantenimientoJdbcDao;
import backend.mantenimiento.dao.RegistroMantenimientoDao;
import backend.mantenimiento.dao.OperacionMantenimientoJdbcDao;
import backend.mantenimiento.dao.RegistroMantenimientoJdbcDao;
import backend.mantenimiento.dao.TipoMantenimientoJdbcDao;
import backend.mantenimiento.dao.VehiculoMantenimientoJdbcDao;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.model.ConflictoOperacionMantenimientoException;
import backend.mantenimiento.dao.OperacionMantenimientoDao;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "mycar.catalogo.tests", matches = "true")
class RegistroMantenimientoConcurrenciaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void prepararBaseAislada() {
        String url = variableObligatoria("MYCAR_CATALOGO_TEST_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/"
                + "mycar_mvp_test_rf17_[a-f0-9]{8}")) {
            throw new IllegalStateException("Solo se permite la base temporal RF17");
        }
        dataSource = new DriverManagerDataSource(
                url,
                variableObligatoria("MYCAR_CATALOGO_TEST_USER"),
                variableObligatoria("MYCAR_CATALOGO_TEST_PASSWORD")
        );
        var jdbc = new JdbcTemplate(dataSource);
        int version = jdbc.queryForObject("SHOW server_version_num", Integer.class);
        if (version < 170000 || version >= 180000) {
            throw new IllegalStateException("Se requiere PostgreSQL 17 aislado");
        }
        Flyway.configure().dataSource(dataSource).schemas("mycar")
                .defaultSchema("mycar").locations("classpath:db/mvp")
                .cleanDisabled(true).baselineOnMigrate(false).load().migrate();
    }

    @Test
    @Timeout(30)
    void RF17_CP02_EA01_revalidaCuandoRf14ConfirmaAntes() throws Exception {
        var fixture = crearFixtureConfirmada();
        var pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> insertarLecturaRf14(fixture)).get(10, TimeUnit.SECONDS);

            assertThrows(IllegalArgumentException.class,
                    () -> servicio(new RegistroMantenimientoJdbcDao(
                            new JdbcTemplate(dataSource)))
                            .registrar(fixture.idUsuario(), solicitud(fixture)));
            assertEquals(0L, cantidadMantenimientos(fixture.idVehiculo()));
            assertEquals(2L, cantidadLecturas(fixture.idVehiculo()));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(30)
    void RF17_CP02_EA01_rf14EsperaCuandoRf17YaValidaConVehiculoBloqueado()
            throws Exception {
        var fixture = crearFixtureConfirmada();
        var antesDelInsert = new CountDownLatch(1);
        var permitirInsert = new CountDownLatch(1);
        var rf14IntentaInsert = new CountDownLatch(1);
        var rf14Confirmo = new CountDownLatch(1);
        RegistroMantenimientoDao daoConPausa = (usuario, tipo, pedido) -> {
            antesDelInsert.countDown();
            try {
                if (!permitirInsert.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("RF17 no obtuvo señal de continuación");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("RF17 interrumpido", ex);
            }
            return new RegistroMantenimientoJdbcDao(
                    new JdbcTemplate(dataSource)).insertar(usuario, tipo, pedido);
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var servicio = servicio(daoConPausa);
            Future<Long> registro = pool.submit(() -> servicio.registrar(
                    fixture.idUsuario(), solicitud(fixture)));
            assertTrue(antesDelInsert.await(10, TimeUnit.SECONDS),
                    "RF17 no llegó al punto controlado después de consultar lecturas");
            Future<?> lectura = pool.submit(() -> {
                rf14IntentaInsert.countDown();
                insertarLecturaRf14(fixture);
                rf14Confirmo.countDown();
            });
            assertTrue(rf14IntentaInsert.await(10, TimeUnit.SECONDS));
            boolean confirmoAntesDeLiberarRf17 = rf14Confirmo.await(2, TimeUnit.SECONDS);
            permitirInsert.countDown();
            Long id = registro.get(10, TimeUnit.SECONDS);
            lectura.get(10, TimeUnit.SECONDS);
            assertEquals(1L, cantidadMantenimientos(fixture.idVehiculo()));
            assertEquals(2L, cantidadLecturas(fixture.idVehiculo()));
            assertEquals(21000, new JdbcTemplate(dataSource).queryForObject(
                    "SELECT km_servicio FROM mycar.mantenimiento WHERE id_mantenimiento = ?",
                    Integer.class, id));
            assertFalse(confirmoAntesDeLiberarRf17,
                    "RF14 confirmó 20500 km antes de que RF17 insertara su servicio "
                            + "histórico de 21000 km: la validación quedó obsoleta");
        } finally {
            permitirInsert.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void revision_mismaSolicitudDevuelveMismoIdYUnaSolaCopiaDeRepuestos() {
        var f = crearFixtureConfirmada();
        var original = solicitud(f);
        var pedido = new SolicitudRegistroMantenimiento(original.idOperacion(), f.idVehiculo(),
                original.datos(), "Revisión", List.of(new backend.mantenimiento.model.DatosRepuesto(
                        "Filtro", null, 2, new java.math.BigDecimal("12.50"))));
        var servicio = servicio(new RegistroMantenimientoJdbcDao(new JdbcTemplate(dataSource)));
        var primero = servicio.registrarResultado(f.idUsuario(), pedido);
        var segundo = servicio.registrarResultado(f.idUsuario(), pedido);
        assertEquals(primero, segundo);
        assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
        assertEquals(1L, new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM mycar.repuesto WHERE id_usuario = ?", Long.class, f.idUsuario()));
        assertEquals(1L, new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM mycar.mantenimiento_repuesto WHERE id_mantenimiento = ?",
                Long.class, primero.idMantenimiento()));
        assertEquals("CONFIRMADA", servicio.consultarOperacion(
                f.idUsuario(), pedido.idOperacion()).estado());
    }

    @Test
    void revision_mismaOperacionConContenidoDistintoSeRechaza() {
        var f = crearFixtureConfirmada();
        var pedido = solicitud(f);
        var servicio = servicio(new RegistroMantenimientoJdbcDao(new JdbcTemplate(dataSource)));
        servicio.registrar(f.idUsuario(), pedido);
        var cambiado = new SolicitudRegistroMantenimiento(pedido.idOperacion(), pedido.idVehiculo(),
                pedido.datos(), "Contenido diferente", pedido.repuestos());
        assertThrows(ConflictoOperacionMantenimientoException.class,
                () -> servicio.registrar(f.idUsuario(), cambiado));
        assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
    }

    @Test
    void revision_reintentoDevuelveSnapshotAunqueVehiculoEsteInactivo() {
        var f = crearFixtureConfirmada();
        var pedido = solicitud(f);
        var servicio = servicio(new RegistroMantenimientoJdbcDao(new JdbcTemplate(dataSource)));
        var original = servicio.registrarResultado(f.idUsuario(), pedido);
        new JdbcTemplate(dataSource).update(
                "UPDATE mycar.vehiculo SET estado_vehiculo = 'INACTIVO' WHERE id_vehiculo = ?",
                f.idVehiculo());
        assertEquals(original, servicio.registrarResultado(f.idUsuario(), pedido));
        assertEquals(original, servicio.consultarOperacion(f.idUsuario(), pedido.idOperacion()).resultado());
        assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
    }

    @Test
    void revision_noPermiteReusarNiConsultarOperacionDeOtroUsuarioOTipo() {
        var f = crearFixtureConfirmada();
        var ajeno = crearFixtureConfirmada();
        var pedido = solicitud(f);
        var servicio = servicio(new RegistroMantenimientoJdbcDao(new JdbcTemplate(dataSource)));
        servicio.registrar(f.idUsuario(), pedido);
        assertThrows(ConflictoOperacionMantenimientoException.class,
                () -> servicio.registrar(ajeno.idUsuario(), pedido));
        assertThrows(ConflictoOperacionMantenimientoException.class,
                () -> servicio.consultarOperacion(ajeno.idUsuario(), pedido.idOperacion()));
        var otroTipo = new SolicitudRegistroMantenimiento(f.operacionRf14(), f.idVehiculo(),
                pedido.datos(), null, List.of());
        assertThrows(ConflictoOperacionMantenimientoException.class,
                () -> servicio.registrar(f.idUsuario(), otroTipo));
        assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
    }

    @Test
    @Timeout(30)
    void revision_dosSolicitudesSimultaneasSoloGuardanUnaVez() throws Exception {
        var f = crearFixtureConfirmada();
        var pedido = solicitud(f);
        var insertando = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        var segundoIntento = new CountDownLatch(1);
        RegistroMantenimientoDao pausado = (usuario, tipo, solicitud) -> {
            insertando.countDown();
            try {
                if (!continuar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Sin señal");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return new RegistroMantenimientoJdbcDao(new JdbcTemplate(dataSource))
                    .insertar(usuario, tipo, solicitud);
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var primero = pool.submit(() -> servicio(pausado).registrar(f.idUsuario(), pedido));
            assertTrue(insertando.await(10, TimeUnit.SECONDS));
            var segundo = pool.submit(() -> {
                segundoIntento.countDown();
                return servicio(new RegistroMantenimientoJdbcDao(new JdbcTemplate(dataSource)))
                        .registrar(f.idUsuario(), pedido);
            });
            assertTrue(segundoIntento.await(10, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> segundo.get(300, TimeUnit.MILLISECONDS));
            continuar.countDown();
            assertEquals(primero.get(10, TimeUnit.SECONDS), segundo.get(10, TimeUnit.SECONDS));
            assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
        } finally {
            continuar.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void revision_falloAlConfirmarRevierteTodoYPermiteReintentarMismaOperacion() {
        var f = crearFixtureConfirmada();
        var original = solicitud(f);
        var pedido = new SolicitudRegistroMantenimiento(original.idOperacion(), f.idVehiculo(),
                original.datos(), null, List.of(new backend.mantenimiento.model.DatosRepuesto(
                        "Filtro", null, 1, null)));
        var jdbc = new JdbcTemplate(dataSource);
        var operaciones = new OperacionMantenimientoJdbcDao(jdbc) {
            @Override
            public void confirmar(UUID id, Long usuario, ResultadoRegistroMantenimiento resultado) {
                super.confirmar(id, usuario, resultado);
                throw new IllegalStateException("Fallo controlado antes del COMMIT");
            }
        };
        assertThrows(IllegalStateException.class,
                () -> servicio(new RegistroMantenimientoJdbcDao(jdbc), operaciones)
                        .registrar(f.idUsuario(), pedido));
        assertEquals(0L, cantidadMantenimientos(f.idVehiculo()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.repuesto WHERE id_usuario = ?", Long.class, f.idUsuario()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.operacion_idempotente WHERE id_operacion = ?",
                Long.class, pedido.idOperacion()));
        var servicio = servicio(new RegistroMantenimientoJdbcDao(jdbc));
        servicio.registrar(f.idUsuario(), pedido);
        assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
    }

    @Test
    void revision_lecturaAntiguaNoPermiteServicioActualConKmInferiorAlDeAyer() {
        var f = crearFixtureConfirmada();
        var jdbc = new JdbcTemplate(dataSource);
        Long inicial = jdbc.queryForObject(
                "SELECT id_lectura_km FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?",
                Long.class, f.idVehiculo());
        jdbc.update("""
                INSERT INTO mycar.lectura_kilometraje
                    (id_vehiculo, valor_km, origen, fecha_lectura_utc, id_operacion)
                VALUES (?, 30000, 'ACTUALIZACION', ?, ?)
                """, f.idVehiculo(), f.fechaServicio().atTime(12, 0).atZone(LIMA).toOffsetDateTime(),
                f.operacionRf14());
        var pedido = new SolicitudRegistroMantenimiento(UUID.randomUUID(), f.idVehiculo(),
                new DatosRegistroMantenimiento("MANTENIMIENTO_GENERAL", LocalDate.now(LIMA),
                        20000, OrigenKmServicio.LECTURA, inicial, null, null, null, null), null, List.of());
        var servicio = servicio(new RegistroMantenimientoJdbcDao(jdbc));
        assertThrows(IllegalArgumentException.class, () -> servicio.registrar(f.idUsuario(), pedido));
        assertEquals(0L, cantidadMantenimientos(f.idVehiculo()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.operacion_idempotente WHERE id_operacion = ?",
                Long.class, pedido.idOperacion()));
        // El mismo kilometraje sí es válido para el servicio histórico del día de su lectura.
        var historico = new SolicitudRegistroMantenimiento(UUID.randomUUID(), f.idVehiculo(),
                new DatosRegistroMantenimiento("MANTENIMIENTO_GENERAL", f.fechaServicio().minusDays(1),
                        20000, OrigenKmServicio.LECTURA, inicial, null, null, null, null), null, List.of());
        servicio.registrar(f.idUsuario(), historico);
        assertEquals(1L, cantidadMantenimientos(f.idVehiculo()));
    }

    private RegistroMantenimientoService servicio(RegistroMantenimientoDao dao) {
        return servicio(dao, new OperacionMantenimientoJdbcDao(new JdbcTemplate(dataSource)));
    }

    private RegistroMantenimientoService servicio(
            RegistroMantenimientoDao dao, OperacionMantenimientoDao operaciones) {
        var jdbc = new JdbcTemplate(dataSource);
        var target = new RegistroMantenimientoService(
                new ValidadorRegistroMantenimiento(Clock.system(LIMA)),
                new ValidadorRepuesto(),
                new VehiculoMantenimientoService(new VehiculoMantenimientoJdbcDao(jdbc)),
                new TipoMantenimientoService(new TipoMantenimientoJdbcDao(jdbc)),
                new LecturaKmMantenimientoService(new LecturaKmMantenimientoJdbcDao(jdbc)),
                dao, operaciones
        );
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(dataSource));
        interceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (RegistroMantenimientoService) proxy.getProxy();
    }

    private Fixture crearFixtureConfirmada() {
        var jdbc = new JdbcTemplate(dataSource);
        Long usuario = jdbc.queryForObject("""
                INSERT INTO mycar.usuario (
                    correo, password_hash, nombre, apellido, fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,
                    consentimiento_privacidad_version
                ) VALUES (?, repeat('x', 60), 'Prueba', 'RF17',
                          CURRENT_DATE - INTERVAL '30 years',
                          clock_timestamp(), 'test-v1') RETURNING id_usuario
                """, Long.class, "rf17-carrera-" + UUID.randomUUID() + "@example.test");
        String placa = "Z9Z" + String.format("%03d", usuario % 1000);
        Long vehiculo = jdbc.queryForObject("""
                INSERT INTO mycar.vehiculo (
                    id_usuario, placa, marca, modelo, uso_vehiculo_codigo,
                    anio_fabricacion, color, id_tipo_motor
                ) VALUES (?, ?, 'Marca prueba', 'Modelo prueba', 'PARTICULAR',
                          2020, 'Azul', (SELECT id_tipo_motor FROM mycar.tipo_motor
                                         WHERE codigo = 'ELECTRICO'))
                RETURNING id_vehiculo
                """, Long.class, usuario, placa);
        UUID inicial = UUID.randomUUID();
        UUID actualizacion = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO mycar.operacion_idempotente (
                    id_operacion, id_usuario, tipo_operacion, hash_solicitud
                ) VALUES (?, ?, 'REGISTRAR_VEHICULO', ?),
                         (?, ?, 'ACTUALIZAR_KM', ?)
                """, inicial, usuario, "a".repeat(64),
                actualizacion, usuario, "b".repeat(64));
        LocalDate fechaServicio = LocalDate.now(LIMA).minusDays(1);
        jdbc.update("""
                INSERT INTO mycar.lectura_kilometraje (
                    id_vehiculo, valor_km, origen, fecha_lectura_utc, id_operacion
                ) VALUES (?, 20000, 'INICIAL', ?, ?)
                """, vehiculo,
                fechaServicio.minusDays(1).atTime(12, 0).atZone(LIMA)
                        .toOffsetDateTime(), inicial);
        return new Fixture(usuario, vehiculo, actualizacion, fechaServicio);
    }

    private void insertarLecturaRf14(Fixture fixture) {
        var jdbc = new JdbcTemplate(dataSource);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '8s'");
                    jdbc.update("""
                            INSERT INTO mycar.lectura_kilometraje (
                                id_vehiculo, valor_km, origen, id_operacion
                            ) VALUES (?, 20500, 'ACTUALIZACION', ?)
                            """, fixture.idVehiculo(), fixture.operacionRf14());
                });
    }

    private SolicitudRegistroMantenimiento solicitud(Fixture fixture) {
        return new SolicitudRegistroMantenimiento(
                UUID.randomUUID(), fixture.idVehiculo(),
                new DatosRegistroMantenimiento(
                        "MANTENIMIENTO_GENERAL", fixture.fechaServicio(),
                        21000, OrigenKmServicio.HISTORICO_MANUAL, null,
                        null, null, null, null), null, List.of());
    }

    private long cantidadMantenimientos(Long vehiculo) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM mycar.mantenimiento WHERE id_vehiculo = ?",
                Long.class, vehiculo);
    }

    private long cantidadLecturas(Long vehiculo) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?",
                Long.class, vehiculo);
    }

    private static String variableObligatoria(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta variable de base temporal " + nombre);
        }
        return valor;
    }

    private record Fixture(Long idUsuario, Long idVehiculo,
                           UUID operacionRf14, LocalDate fechaServicio) { }
}
