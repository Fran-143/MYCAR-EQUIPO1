package backend.mantenimiento.dao;

import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import backend.mantenimiento.service.LecturaKmMantenimientoService;
import backend.mantenimiento.service.RegistroMantenimientoService;
import backend.mantenimiento.service.TipoMantenimientoService;
import backend.mantenimiento.service.ValidadorRegistroMantenimiento;
import backend.mantenimiento.service.ValidadorRepuesto;
import backend.mantenimiento.service.VehiculoMantenimientoService;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

@EnabledIfSystemProperty(
        named = "mycar.catalogo.tests",
        matches = "true"
)
class RegistroMantenimientoJdbcDaoTest {

    private static DriverManagerDataSource dataSource;

    private DataSourceTransactionManager gestor;
    private TransactionStatus transaccion;
    private JdbcTemplate jdbc;
    private RegistroMantenimientoJdbcDao dao;

    private Long idUsuario;
    private Long idVehiculo;
    private Long idLectura;
    private Long idTipo;
    private LocalDate fechaServicio;

    @BeforeAll
    static void prepararBase() {
        String url = variableObligatoria("MYCAR_CATALOGO_TEST_URL");
        String usuario = variableObligatoria("MYCAR_CATALOGO_TEST_USER");
        String password = variableObligatoria(
                "MYCAR_CATALOGO_TEST_PASSWORD"
        );

        if (!url.matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:\\d+/"
                        + "mycar_mvp_test_rf17_[a-f0-9]{8}"
        )) {
            throw new IllegalStateException(
                    "La URL debe apuntar a la BD temporal de RF17"
            );
        }

        dataSource = new DriverManagerDataSource(
                url, usuario, password
        );

        Flyway.configure()
                .dataSource(dataSource)
                .schemas("mycar")
                .defaultSchema("mycar")
                .locations("classpath:db/mvp")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    @BeforeEach
    void prepararDatos() {
        gestor = new DataSourceTransactionManager(dataSource);
        transaccion = gestor.getTransaction(
                new DefaultTransactionDefinition()
        );

        jdbc = new JdbcTemplate(dataSource);
        dao = new RegistroMantenimientoJdbcDao(jdbc);

        fechaServicio = LocalDate.now(ZoneId.of("America/Lima"));

        idUsuario = jdbc.queryForObject(
                """
                INSERT INTO mycar.usuario (
                    correo, password_hash, nombre, apellido,
                    fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,
                    consentimiento_privacidad_version
                )
                VALUES (
                    ?, repeat('x', 60), 'Prueba', 'RF17',
                    CURRENT_DATE - INTERVAL '30 years',
                    clock_timestamp(), 'test-v1'
                )
                RETURNING id_usuario
                """,
                Long.class,
                "rf17-" + UUID.randomUUID() + "@example.test"
        );

        idVehiculo = jdbc.queryForObject(
                """
                INSERT INTO mycar.vehiculo (
                    id_usuario, placa, marca, modelo,
                    uso_vehiculo_codigo, anio_fabricacion,
                    color, id_tipo_motor
                )
                VALUES (
                    ?, 'A1B234', 'Marca prueba', 'Modelo prueba',
                    'PARTICULAR', 2020, 'Azul',
                    (
                        SELECT id_tipo_motor
                        FROM mycar.tipo_motor
                        WHERE codigo = 'ELECTRICO'
                    )
                )
                RETURNING id_vehiculo
                """,
                Long.class,
                idUsuario
        );

        UUID operacionLectura = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO mycar.operacion_idempotente (
                    id_operacion, id_usuario,
                    tipo_operacion, hash_solicitud
                )
                VALUES (?, ?, 'REGISTRAR_VEHICULO', ?)
                """,
                operacionLectura,
                idUsuario,
                "a".repeat(64)
        );

        idLectura = jdbc.queryForObject(
                """
                INSERT INTO mycar.lectura_kilometraje (
                    id_vehiculo, valor_km, origen,
                    fecha_lectura_utc, id_operacion
                )
                VALUES (?, 21000, 'INICIAL', ?, ?)
                RETURNING id_lectura_km
                """,
                Long.class,
                idVehiculo,
                fechaServicio.atStartOfDay(
                        ZoneId.of("America/Lima")
                ).toOffsetDateTime(),
                operacionLectura
        );

        idTipo = jdbc.queryForObject(
                """
                SELECT id_tipo_mantenimiento
                FROM mycar.tipo_mantenimiento
                WHERE codigo = 'MANTENIMIENTO_GENERAL'
                """,
                Long.class
        );
    }

    @AfterEach
    void revertirDatos() {
        if (transaccion != null && !transaccion.isCompleted()) {
            gestor.rollback(transaccion);
        }
    }

    @Test
    void RF17_CP04_EA03_guardaMantenimientoConRepuesto() {
        var repuesto = new DatosRepuesto(
                "Filtro de aceite",
                "Bosch",
                2,
                new BigDecimal("35.00")
        );

        var solicitud = solicitud(
                new BigDecimal("150.00"),
                List.of(repuesto)
        );

        Long id = dao.insertar(idUsuario, idTipo, solicitud);

        assertNotNull(id);

        jdbc.queryForObject(
                """
                SELECT *
                FROM mycar.mantenimiento
                WHERE id_mantenimiento = ?
                """,
                (rs, fila) -> {
                    assertEquals(
                            idVehiculo.longValue(),
                            rs.getLong("id_vehiculo")
                    );
                    assertEquals(
                            idTipo.longValue(),
                            rs.getLong("id_tipo_mantenimiento")
                    );
                    assertEquals(21000, rs.getInt("km_servicio"));
                    assertEquals(
                            fechaServicio,
                            rs.getDate("fecha_servicio").toLocalDate()
                    );
                    assertEquals(
                            "LECTURA",
                            rs.getString("origen_km_servicio")
                    );
                    assertEquals(
                            idLectura.longValue(),
                            rs.getLong("id_lectura_km_referencia")
                    );
                    assertEquals(
                            new BigDecimal("150.00"),
                            rs.getBigDecimal("costo_total")
                    );
                    assertNull(rs.getString("servicio_otro"));
                    assertEquals(
                            "REALIZADO",
                            rs.getString("estado")
                    );
                    assertEquals(
                            solicitud.observaciones(),
                            rs.getString("descripcion")
                    );
                    assertEquals(
                            fechaServicio.plusMonths(6),
                            rs.getDate("proxima_fecha_sugerida")
                                    .toLocalDate()
                    );
                    assertEquals(
                            26000,
                            rs.getInt("proximo_km_sugerido")
                    );
                    return rs.getLong("id_mantenimiento");
                },
                id
        );

        var repuestos = jdbc.queryForList(
                """
                SELECT r.id_usuario,
                       r.nombre,
                       r.marca,
                       mr.nombre_snapshot,
                       mr.cantidad,
                       mr.precio_unitario
                FROM mycar.mantenimiento_repuesto mr
                JOIN mycar.repuesto r
                    ON r.id_repuesto = mr.id_repuesto
                WHERE mr.id_mantenimiento = ?
                """,
                id
        );

        assertEquals(1, repuestos.size());

        var guardado = repuestos.get(0);

        assertEquals(idUsuario, guardado.get("id_usuario"));
        assertEquals("Filtro de aceite", guardado.get("nombre"));
        assertEquals("Bosch", guardado.get("marca"));
        assertEquals(
                "Filtro de aceite",
                guardado.get("nombre_snapshot")
        );
        assertEquals(2, guardado.get("cantidad"));
        assertEquals(
                new BigDecimal("35.00"),
                guardado.get("precio_unitario")
        );
    }

    @Test
    void RF17_CP01_EF_guardaSinRepuestosYSinCostoInformado() {
        var solicitud = solicitud(null, List.of());

        Long id = dao.insertar(idUsuario, idTipo, solicitud);

        assertNotNull(id);

        var guardado = jdbc.queryForMap(
                """
                SELECT costo_total, descripcion
                FROM mycar.mantenimiento
                WHERE id_mantenimiento = ?
                """,
                id
        );

        assertNull(guardado.get("costo_total"));
        assertEquals(
                solicitud.observaciones(),
                guardado.get("descripcion")
        );

        Long cantidad = jdbc.queryForObject(
                """
                SELECT count(*)
                FROM mycar.mantenimiento_repuesto
                WHERE id_mantenimiento = ?
                """,
                Long.class,
                id
        );

        assertEquals(Long.valueOf(0L), cantidad);
    }

    @Test
    void RF17_CP04_EA03_guardaDosRepuestosConNombreRepetidoYAsociacionesDistintas() {
        var solicitud = solicitud(null, List.of(
                new DatosRepuesto("Filtro", "Bosch", 1, new BigDecimal("35.00")),
                new DatosRepuesto("Filtro", null, 3, null)
        ));
        Long lecturaAnterior = jdbc.queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?",
                Long.class, idVehiculo);

        Long id = dao.insertar(idUsuario, idTipo, solicitud);

        var filas = jdbc.queryForList(
                """
                SELECT r.id_repuesto, r.id_usuario, r.nombre, r.marca,
                       mr.nombre_snapshot, mr.cantidad, mr.precio_unitario
                FROM mycar.mantenimiento_repuesto mr
                JOIN mycar.repuesto r ON r.id_repuesto = mr.id_repuesto
                WHERE mr.id_mantenimiento = ? ORDER BY r.id_repuesto
                """, id);
        assertEquals(2, filas.size());
        assertNotEquals(filas.get(0).get("id_repuesto"),
                filas.get(1).get("id_repuesto"));
        assertEquals(List.of(1, 3), filas.stream()
                .map(fila -> fila.get("cantidad")).toList());
        for (var fila : filas) {
            assertEquals(idUsuario, fila.get("id_usuario"));
            assertEquals("Filtro", fila.get("nombre"));
            assertEquals("Filtro", fila.get("nombre_snapshot"));
        }
        assertEquals("Bosch", filas.get(0).get("marca"));
        assertNull(filas.get(1).get("marca"));
        assertEquals(new BigDecimal("35.00"), filas.get(0).get("precio_unitario"));
        assertNull(filas.get(1).get("precio_unitario"));
        assertEquals(lecturaAnterior, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.alerta WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
    }

    @Test
    void RF17_CP02_EA01_guardaKmHistoricoManualYCostoCeroSinAlterarOdometro() {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL", fechaServicio.minusDays(1),
                19000, OrigenKmServicio.HISTORICO_MANUAL, null,
                new BigDecimal("0.00"), null, null, null
        );
        var solicitud = new SolicitudRegistroMantenimiento(
                UUID.randomUUID(), idVehiculo, datos, null, List.of()
        );

        Long id = servicioConTransaccion(dao).registrar(idUsuario, solicitud);

        var mantenimiento = jdbc.queryForMap(
                """
                SELECT km_servicio, fecha_servicio, origen_km_servicio,
                       id_lectura_km_referencia, costo_total, estado
                FROM mycar.mantenimiento WHERE id_mantenimiento = ?
                """, id);
        assertEquals(19000, mantenimiento.get("km_servicio"));
        assertEquals(fechaServicio.minusDays(1),
                ((java.sql.Date) mantenimiento.get("fecha_servicio")).toLocalDate());
        assertEquals("HISTORICO_MANUAL", mantenimiento.get("origen_km_servicio"));
        assertNull(mantenimiento.get("id_lectura_km_referencia"));
        assertEquals(new BigDecimal("0.00"), mantenimiento.get("costo_total"));
        assertEquals("REALIZADO", mantenimiento.get("estado"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
        assertEquals(21000, jdbc.queryForObject(
                "SELECT valor_km FROM mycar.lectura_kilometraje WHERE id_lectura_km = ?",
                Integer.class, idLectura));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.alerta WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
    }

    private SolicitudRegistroMantenimiento solicitud(
            BigDecimal costo,
            List<DatosRepuesto> repuestos
    ) {
        var datos = new DatosRegistroMantenimiento(
                "MANTENIMIENTO_GENERAL",
                fechaServicio,
                21000,
                OrigenKmServicio.LECTURA,
                idLectura,
                costo,
                null,
                fechaServicio.plusMonths(6),
                26000
        );

        return new SolicitudRegistroMantenimiento(
                UUID.randomUUID(),
                idVehiculo,
                datos,
                "Se realizó el mantenimiento general",
                repuestos
        );
    }

    private static String variableObligatoria(String nombre) {
        String valor = System.getenv(nombre);

        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta " + nombre
                            + ". Ejecuta desde el PowerShell "
                            + "donde configuraste PostgreSQL de pruebas."
            );
        }

        return valor;
    }
    @Test
    void RF17_CP08_EA07_revierteTodoAnteFalloDeAlmacenamiento() {
        var repuesto = new DatosRepuesto(
                "Filtro de aceite",
                "Bosch",
                1,
                new BigDecimal("35.00")
        );

        var solicitud = solicitud(
                new BigDecimal("150.00"),
                List.of(repuesto)
        );

        var idInsertado = new AtomicReference<Long>();

        // Primero ejecutamos los INSERT reales.
        // Después simulamos un fallo antes de confirmar la operación.
        RegistroMantenimientoDao daoConFallo = (usuario, tipo, pedido) -> {
            Long id = dao.insertar(usuario, tipo, pedido);
            idInsertado.set(id);

            throw new DataAccessResourceFailureException(
                    "Fallo simulado antes de confirmar"
            );
        };

        var servicio = servicioConTransaccion(daoConFallo);

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> servicio.registrar(idUsuario, solicitud)
        );

        assertNotNull(idInsertado.get());

        assertTrue(
                transaccion.isRollbackOnly(),
                "El servicio debe marcar la transacción para rollback"
        );

        // Terminamos la transacción y comprobamos el resultado en la BD.
        gestor.rollback(transaccion);

        assertEquals(
                Long.valueOf(0L),
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM mycar.mantenimiento
                        WHERE id_mantenimiento = ?
                        """,
                        Long.class,
                        idInsertado.get()
                )
        );

        assertEquals(
                Long.valueOf(0L),
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM mycar.repuesto
                        WHERE id_usuario = ?
                        """,
                        Long.class,
                        idUsuario
                )
        );

        assertEquals(
                Long.valueOf(0L),
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM mycar.mantenimiento_repuesto
                        WHERE id_mantenimiento = ?
                        """,
                        Long.class,
                        idInsertado.get()
                )
        );
    }

    @Test
    void RF17_CP08_EA07_falloEnSegundaAsociacionRevierteTransaccionDelServicio() {
        // Esta fixture se confirma por separado para que el servicio abra su
        // propia transacción. Su placa no interfiere con las demás fixtures.
        String placa = "Z9Z" + String.format("%03d", idVehiculo % 1000);
        jdbc.update("UPDATE mycar.vehiculo SET placa = ? WHERE id_vehiculo = ?",
                placa, idVehiculo);
        gestor.commit(transaccion);

        var jdbcConFallo = new JdbcTemplate(dataSource) {
            private int asociaciones;

            @Override
            public int update(String sql, Object... argumentos) {
                if (sql.contains("INSERT INTO mycar.mantenimiento_repuesto")
                        && ++asociaciones == 2) {
                    throw new DataAccessResourceFailureException(
                            "Fallo simulado en la segunda asociación"
                    );
                }
                return super.update(sql, argumentos);
            }
        };
        var solicitud = solicitud(new BigDecimal("150.00"), List.of(
                new DatosRepuesto("Filtro", "Bosch", 1, new BigDecimal("35.00")),
                new DatosRepuesto("Bujía", "NGK", 2, new BigDecimal("20.00"))
        ));
        var servicio = servicioConTransaccion(
                new RegistroMantenimientoJdbcDao(jdbcConFallo));

        assertThrows(DataAccessResourceFailureException.class,
                () -> servicio.registrar(idUsuario, solicitud));

        // Consultas fuera de la transacción fallida: ni el mantenimiento ni
        // la primera línea provisional quedaron confirmados.
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.mantenimiento WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.repuesto WHERE id_usuario = ?",
                Long.class, idUsuario));
        assertEquals(0L, jdbc.queryForObject(
                """
                SELECT count(*) FROM mycar.mantenimiento_repuesto mr
                JOIN mycar.mantenimiento m ON m.id_mantenimiento = mr.id_mantenimiento
                WHERE m.id_vehiculo = ?
                """, Long.class, idVehiculo));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM mycar.alerta WHERE id_vehiculo = ?",
                Long.class, idVehiculo));
    }

    private RegistroMantenimientoService servicioConTransaccion(
            RegistroMantenimientoDao daoRegistro
    ) {
        ZoneId lima = ZoneId.of("America/Lima");

        Clock reloj = Clock.fixed(
                fechaServicio.atTime(12, 0).atZone(lima).toInstant(),
                lima
        );

        var servicio = new RegistroMantenimientoService(
                new ValidadorRegistroMantenimiento(reloj),
                new ValidadorRepuesto(),
                new VehiculoMantenimientoService(
                        new VehiculoMantenimientoJdbcDao(jdbc)
                ),
                new TipoMantenimientoService(
                        new TipoMantenimientoJdbcDao(jdbc)
                ),
                new LecturaKmMantenimientoService(
                        new LecturaKmMantenimientoJdbcDao(jdbc)
                ),
                daoRegistro
        );

        // Aplicamos el control transaccional de Spring al servicio.
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(gestor);
        interceptor.setTransactionAttributeSource(
                new AnnotationTransactionAttributeSource()
        );
        interceptor.afterPropertiesSet();

        var proxy = new ProxyFactory(servicio);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);

        return (RegistroMantenimientoService) proxy.getProxy();
    }
}
