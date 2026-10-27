package backend.historial.dao;

import backend.historial.model.DatosHistorial.*;
import backend.historial.model.HistorialNoDisponible;
import backend.historial.service.HistorialMantenimientoService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures exclusivamente en PostgreSQL temporal. Nunca utiliza .env ni borra una base existente. */
@EnabledIfSystemProperty(named = "mycar.rf18.tests", matches = "true")
class HistorialMantenimientoPostgresTest {
    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource fuente;
    private static long usuario, otroUsuario, activo, inactivo, ajeno, antiguo, anulado, otro, tipoInactivo;

    @BeforeAll static void preparar() {
        String url = requerido("MYCAR_CATALOGO_TEST_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/mycar_mvp_test_rf(?:17|18)_[a-f0-9]{8}")) {
            throw new IllegalStateException("RF18 solo admite una BD temporal RF17/RF18 en localhost");
        }
        fuente = new DriverManagerDataSource(url, requerido("MYCAR_CATALOGO_TEST_USER"), requerido("MYCAR_CATALOGO_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(fuente);
        int version = jdbc.queryForObject("SHOW server_version_num", Integer.class);
        if (version < 170000 || version >= 180000) throw new IllegalStateException("Se requiere PostgreSQL 17");
        Flyway.configure().dataSource(fuente).schemas("mycar").defaultSchema("mycar")
                .locations("classpath:db/mvp").cleanDisabled(true).baselineOnMigrate(false).load().migrate();
        new TransactionTemplate(new DataSourceTransactionManager(fuente)).executeWithoutResult(s -> {
            usuario = cuenta(); otroUsuario = cuenta();
            activo = vehiculo(usuario, "X8X" + String.format("%03d", usuario % 1000));
            inactivo = vehiculo(usuario, "X9X" + String.format("%03d", usuario % 1000));
            ajeno = vehiculo(otroUsuario, "Y8Y" + String.format("%03d", otroUsuario % 1000));
            jdbc.update("UPDATE mycar.vehiculo SET es_principal=true WHERE id_vehiculo=?", activo);
            jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", inactivo);
            var lima = ZoneId.of("America/Lima"); var hoy = LocalDate.now(lima);
            UUID inicial = UUID.randomUUID(), actual = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO mycar.operacion_idempotente (id_operacion,id_usuario,tipo_operacion,hash_solicitud)
                    VALUES (?,?,'REGISTRAR_VEHICULO',?), (?,?,'ACTUALIZAR_KM',?)
                    """, inicial, usuario, "a".repeat(64), actual, usuario, "b".repeat(64));
            jdbc.update("""
                    INSERT INTO mycar.lectura_kilometraje (id_vehiculo,valor_km,origen,fecha_lectura_utc,id_operacion)
                    VALUES (?,20000,'INICIAL',?,?), (?,60000,'ACTUALIZACION',?,?)
                    """, activo, hoy.minusDays(10).atTime(12,0).atZone(lima).toOffsetDateTime(), inicial,
                    activo, hoy.minusDays(1).atTime(12,0).atZone(lima).toOffsetDateTime(), actual);
            antiguo = mantenimiento(activo, "CAMBIO_ACEITE", hoy.minusDays(5), 47000, "REALIZADO", null, null);
            anulado = mantenimiento(activo, "MANTENIMIENTO_GENERAL", hoy.minusDays(3), 49000, "ANULADO", 0, null);
            otro = mantenimiento(activo, "OTRO", hoy.minusDays(3), 49000, "REALIZADO", new java.math.BigDecimal("180.50"), "Alineación");
            String codigo = "RF18_HIST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            jdbc.update("INSERT INTO mycar.tipo_mantenimiento (codigo,nombre,activo) VALUES (?,?,true)", codigo, "Servicio histórico " + codigo);
            tipoInactivo = mantenimiento(activo, codigo, hoy.minusDays(7), 30000, "REALIZADO", null, null);
            jdbc.update("UPDATE mycar.tipo_mantenimiento SET activo=false WHERE codigo=?", codigo);
            mantenimiento(ajeno, "MANTENIMIENTO_GENERAL", hoy.minusDays(3), 10000, "REALIZADO", null, null);
        });
    }

    @Test void CP01_ordenEstadosCostosYKmPersistidoSinConfundirConRF14() {
        var resultado = servicio().consultar(usuario, activo);
        assertEquals(4, resultado.total());
        assertEquals(List.of(otro, anulado, antiguo, tipoInactivo), resultado.mantenimientos().stream().map(Mantenimiento::idMantenimiento).toList());
        assertEquals(49000, resultado.mantenimientos().getFirst().kmServicio());
        assertEquals("ANULADO", resultado.mantenimientos().get(1).estado());
        assertEquals(0, resultado.mantenimientos().get(1).costoTotal().signum());
        assertNull(resultado.mantenimientos().get(2).costoTotal());
        assertEquals(47000, resultado.mantenimientos().get(2).kmServicio());
        assertEquals(60000, jdbc.queryForObject("SELECT max(valor_km) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", Integer.class, activo));
        assertEquals("Alineación", resultado.mantenimientos().getFirst().servicioOtro());
    }

    @Test void CP02_vehiculoInactivoSinHistorialDevuelveCero() {
        var resultado = servicio().consultar(usuario, inactivo);
        assertEquals("INACTIVO", resultado.vehiculo().estado());
        assertEquals(0, resultado.total());
    }

    @Test void CP03_sinVehiculosDevuelveListaVacia() {
        long vacio = cuenta();
        assertTrue(servicio().contexto(vacio).vehiculos().isEmpty());
    }

    @Test void CP04_contextoIncluyePropiosActivosEInactivosYPrincipal() {
        var lista = servicio().contexto(usuario).vehiculos();
        assertEquals(List.of(activo, inactivo), lista.stream().map(Vehiculo::idVehiculo).toList());
        assertTrue(lista.getFirst().principal()); assertFalse(lista.getLast().principal());
        assertEquals("INACTIVO", lista.getLast().estado());
        // El otro propietario no tiene principal y no se le asigna uno al consultar.
        assertFalse(servicio().contexto(otroUsuario).vehiculos().getFirst().principal());
    }

    @Test void CP05_noExponeAjenoNiInexistenteInclusoAlLlamarDaoDirectamente() {
        assertThrows(HistorialNoDisponible.class, () -> servicio().consultar(usuario, ajeno));
        assertThrows(HistorialNoDisponible.class, () -> servicio().consultar(usuario, Long.MAX_VALUE));
        assertTrue(new HistorialMantenimientoJdbcDao(jdbc).mantenimientosPropios(usuario, ajeno).isEmpty());
    }

    @Test void soloLecturaRealNoAlteraRegistrosPrincipalLecturasNiAlertas() {
        var antes = snapshot(); var servicio = servicio();
        servicio.contexto(usuario); servicio.consultar(usuario, activo); servicio.consultar(usuario, inactivo);
        assertEquals(antes, snapshot());
    }

    private HistorialMantenimientoService servicio() {
        var dao = new HistorialMantenimientoJdbcDao(jdbc) {
            @Override public List<Vehiculo> vehiculosPropios(long u) {
                assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                return super.vehiculosPropios(u);
            }
            @Override public java.util.Optional<Vehiculo> vehiculoPropio(long u, long v) {
                assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                return super.vehiculoPropio(u, v);
            }
        };
        var gestor = new DataSourceTransactionManager(fuente); gestor.setEnforceReadOnly(true);
        var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(gestor);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); interceptor.afterPropertiesSet();
        var proxy = new ProxyFactory(new HistorialMantenimientoService(dao)); proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor);
        return (HistorialMantenimientoService) proxy.getProxy();
    }

    private Map<String,Object> snapshot() {
        return jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM mycar.mantenimiento) AS mantenimientos,
                       (SELECT count(*) FROM mycar.lectura_kilometraje) AS lecturas,
                       (SELECT count(*) FROM mycar.alerta) AS alertas,
                       (SELECT count(*) FROM mycar.historial_alerta) AS eventos,
                       (SELECT count(*) FROM mycar.operacion_idempotente) AS operaciones,
                       (SELECT string_agg(id_vehiculo::text || ':' || es_principal::text, ',' ORDER BY id_vehiculo) FROM mycar.vehiculo) AS principales
                """);
    }

    private static long cuenta() {
        return jdbc.queryForObject("""
                INSERT INTO mycar.usuario (correo,password_hash,nombre,apellido,fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
                VALUES (?,repeat('x',60),'Prueba','RF18',CURRENT_DATE - INTERVAL '30 years',clock_timestamp(),'test-v1') RETURNING id_usuario
                """, Long.class, "rf18-" + UUID.randomUUID() + "@example.test");
    }

    private static long vehiculo(long usuario, String placa) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.vehiculo (id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor)
                VALUES (?,?,'Marca prueba','Modelo prueba','PARTICULAR',2020,'Azul',
                    (SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO')) RETURNING id_vehiculo
                """, Long.class, usuario, placa);
    }

    private static long mantenimiento(long vehiculo, String tipo, LocalDate fecha, int km, String estado, Object costo, String otro) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.mantenimiento (id_vehiculo,id_tipo_mantenimiento,fecha_servicio,km_servicio,
                    origen_km_servicio,estado,costo_total,servicio_otro,proximo_km_sugerido)
                VALUES (?,(SELECT id_tipo_mantenimiento FROM mycar.tipo_mantenimiento WHERE codigo=?),?,?,
                    'HISTORICO_MANUAL',?,?,?,70000) RETURNING id_mantenimiento
                """, Long.class, vehiculo, tipo, fecha, km, estado, costo, otro);
    }

    private static String requerido(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) throw new IllegalStateException("Falta variable de BD temporal " + nombre);
        return valor;
    }
}
