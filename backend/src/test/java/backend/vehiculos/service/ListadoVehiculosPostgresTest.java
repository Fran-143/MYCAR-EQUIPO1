package backend.vehiculos.service;

import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Consultas reales de solo lectura; exclusivamente en BD desechable. */
@EnabledIfSystemProperty(named = "mycar.rf08.tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=optional:file:./target/rf08-sin-env.properties",
        "spring.flyway.enabled=false"
})
class ListadoVehiculosPostgresTest {
    private static final AtomicInteger PLACAS = new AtomicInteger(600);
    @Autowired ConsultaRegistroVehiculoService consultas;
    @Autowired RegistroVehiculoService registro;
    @Autowired OperacionVehiculoDao operaciones;
    @Autowired HuellaRegistroVehiculo huellas;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource fuente;
    @Autowired PlatformTransactionManager transacciones;

    @DynamicPropertySource static void destino(DynamicPropertyRegistry propiedades) {
        String url = System.getenv("MYCAR_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/mycar_mvp_test_[a-z0-9_]+")) {
            throw new IllegalStateException("Consulta RF08 exige BD desechable");
        }
        propiedades.add("spring.datasource.url", () -> url);
        propiedades.add("spring.datasource.username", () -> System.getenv("MYCAR_TEST_USER"));
        propiedades.add("spring.datasource.password", () -> System.getenv("MYCAR_TEST_PASSWORD"));
    }

    @Test void devuelveSoloVehiculosPropiosConTarjetaYLecturaReal() {
        long uno = cuenta(true), dos = cuenta(true);
        var propio = registro.registrar(uno, datos());
        var ajeno = registro.registrar(dos, datos());
        var lista = consultas.listarVehiculos(uno, null, null);
        assertEquals("TODOS", lista.filtro());
        assertEquals(1, lista.resumen().todos());
        var tarjeta = lista.vehiculos().getFirst();
        assertEquals(propio.idVehiculo(), tarjeta.idVehiculo());
        assertNotEquals(ajeno.idVehiculo(), tarjeta.idVehiculo());
        assertEquals(50000, tarjeta.kilometrajeActual());
        assertEquals("Toyota", tarjeta.marca());
        assertEquals("Particular", tarjeta.usoVehiculo());
        assertEquals(2020, tarjeta.anioFabricacion());
        assertEquals("Gasolina", tarjeta.combustible());
        assertTrue(tarjeta.principal());
    }
    @Test void cuentaSinVehiculosEsVacioRealSinCrearFreeNiOperaciones() {
        long usuario = cuenta(false);
        var lista = consultas.listarVehiculos(usuario, null, null);
        assertTrue(lista.vehiculos().isEmpty()); assertEquals(0, lista.resumen().todos());
        assertEquals(0, numero("SELECT count(*) FROM mycar.suscripcion WHERE id_usuario=?", usuario));
        assertEquals(0, numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=?", usuario));
    }
    @Test void listaVehiculoInactivoSinExigirPlanOperativo() {
        long usuario = cuenta(true);
        var id = registro.registrar(usuario, datos()).idVehiculo();
        jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", id);
        jdbc.update("UPDATE mycar.suscripcion SET estado='VENCIDA' WHERE id_usuario=?", usuario);
        var lista = consultas.listarVehiculos(usuario, "INACTIVO", "");
        assertEquals(id, lista.vehiculos().getFirst().idVehiculo());
        assertEquals(1, lista.resumen().inactivos()); assertEquals(0, lista.resumen().activos());
        assertTrue(consultas.listarVehiculos(usuario, "ACTIVO", "").vehiculos().isEmpty());
    }
    @Test void busquedaCombinaMarcaModeloYNoDistingueMayusculas() {
        long usuario = cuenta(true); registro.registrar(usuario, datos());
        var lista = consultas.listarVehiculos(usuario, "TODOS", "  toYota   coroLLa  ");
        assertEquals(1, lista.vehiculos().size()); assertEquals("toYota coroLLa", lista.busqueda());
        assertEquals(1, consultas.listarVehiculos(usuario, "TODOS", "Corolla").vehiculos().size());
        assertEquals(1, consultas.listarVehiculos(usuario, "TODOS", lista.vehiculos().getFirst().placa().toLowerCase()).vehiculos().size());
    }
    @Test void sinCoincidenciasConservaContadoresYNuncaInterpretaTextoComoSql() {
        long usuario = cuenta(true); registro.registrar(usuario, datos());
        for (String texto : java.util.List.of("Honda", "%", "_", "' OR 1=1 --")) {
            var lista = consultas.listarVehiculos(usuario, "TODOS", texto);
            assertTrue(lista.vehiculos().isEmpty()); assertEquals(1, lista.resumen().todos());
        }
    }
    @Test void filtrosDesconocidosSeRechazan() {
        long usuario = cuenta(true);
        for (String filtro : java.util.List.of("", "todos", "PREMIUM", "TODOS "))
            assertCodigo("FILTRO_INVALIDO", () -> consultas.listarVehiculos(usuario, filtro, ""));
    }
    @Test void cuentaSuspendidaNoObtieneListado() {
        long usuario = cuenta(true); registro.registrar(usuario, datos());
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA' WHERE id_usuario=?", usuario);
        assertCodigo("CUENTA_NO_AUTORIZADA", () -> consultas.listarVehiculos(usuario, null, null));
    }
    @Test void listarNoEscribeYConsultaLaUltimaLectura() {
        long usuario = cuenta(true);
        var registroInicial = registro.registrar(usuario, datos());
        new TransactionTemplate(transacciones).execute(s -> {
            UUID op = UUID.randomUUID();
            operaciones.reservar(op, usuario, "ACTUALIZAR_KM", "a".repeat(64));
            jdbc.update("INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion) VALUES (?,51000,'ACTUALIZACION',?)", registroInicial.idVehiculo(), op);
            return null;
        });
        long lecturas = numero("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", registroInicial.idVehiculo());
        long ops = numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=?", usuario);
        assertEquals(51000, consultas.listarVehiculos(usuario, null, null).vehiculos().getFirst().kilometrajeActual());
        consultas.listarVehiculos(usuario, "ACTIVO", "Toyota");
        assertEquals(lecturas, numero("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", registroInicial.idVehiculo()));
        assertEquals(ops, numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=?", usuario));
    }
    @Test void electricoNoInventaCombustible() {
        long usuario = cuenta(true); var b = datos();
        var e = new DatosRegistroVehiculo(b.idOperacion(), b.marca(), b.modelo(), b.usoVehiculoCodigo(), b.placa(),
            b.anioFabricacion(), b.color(), jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO'", Long.class),
            null, b.kilometrajeInicial(), null, null, null, null, null, null, null);
        registro.registrar(usuario, e);
        assertNull(consultas.listarVehiculos(usuario, null, null).vehiculos().getFirst().combustible());
    }
    @Test void principalPrimeroSinSaltarseFiltroYContadoresIndependientes() {
        long usuario = cuenta(true);
        var primero = registro.registrar(usuario, datos());
        // Fixture de historial con varios vehículos: no utiliza el alta pública Free para el segundo.
        long segundo = new TransactionTemplate(transacciones).execute(s ->
            new backend.vehiculos.dao.VehiculoDao(jdbc).insertarVehiculo(usuario, datos(), false));
        var todos = consultas.listarVehiculos(usuario, "TODOS", "");
        assertEquals(java.util.List.of(primero.idVehiculo(), segundo),
            todos.vehiculos().stream().map(v -> v.idVehiculo()).toList());
        jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", primero.idVehiculo());
        var activos = consultas.listarVehiculos(usuario, "ACTIVO", "");
        assertEquals(java.util.List.of(segundo), activos.vehiculos().stream().map(v -> v.idVehiculo()).toList());
        assertEquals(2, activos.resumen().todos()); assertEquals(1, activos.resumen().activos());
        assertEquals(1, activos.resumen().inactivos());
    }
    @Test void ausenciaDeLecturaNoSeRepresentaComoCero() {
        long usuario = cuenta(true);
        new TransactionTemplate(transacciones).execute(s ->
            new backend.vehiculos.dao.VehiculoDao(jdbc).insertarVehiculo(usuario, datos(), true));
        assertNull(consultas.listarVehiculos(usuario, null, null).vehiculos().getFirst().kilometrajeActual());
    }

    @Test void detalleSoloPropietarioIncluyeIdentificadoresYLectura() {
        long uno = cuenta(true), dos = cuenta(true);
        var propio = registro.registrar(uno, datos());
        var ajeno = registro.registrar(dos, datos());
        var detalle = consultas.consultarDetalle(uno, propio.idVehiculo());
        assertEquals("SERIE123", detalle.numeroSerie());
        assertEquals("MOTOR123", detalle.numeroMotor());
        assertEquals(50000, detalle.kilometrajeActual());
        assertNotNull(detalle.fechaUltimaLecturaKm());
        assertCodigo("VEHICULO_NO_ENCONTRADO", () -> consultas.consultarDetalle(dos, propio.idVehiculo()));
        assertCodigo("VEHICULO_NO_ENCONTRADO", () -> consultas.consultarDetalle(uno, ajeno.idVehiculo()));
    }

    private long cuenta(boolean free) {
        assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("17."));
        return new TransactionTemplate(transacciones).execute(s -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,
                        consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
                    VALUES (?,repeat('x',60),'Consulta','RF08',?,clock_timestamp(),'v1') RETURNING id_usuario
                    """, Long.class, UUID.randomUUID()+"@example.test", LocalDate.now(ZoneId.of("America/Lima")).minusYears(30));
            if (free) assertEquals(1, jdbc.update("INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) SELECT ?,id_plan,'ACTIVA' FROM mycar.plan WHERE codigo='FREE' AND activo", id));
            jdbc.update("INSERT INTO mycar.preferencia_notificacion(id_usuario) VALUES (?)", id);
            jdbc.update("INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario) VALUES (?)", id);
            return id;
        });
    }

    private DatosRegistroVehiculo datos() {
        return new DatosRegistroVehiculo(UUID.randomUUID(), "Toyota", "Corolla", "PARTICULAR",
                String.format(Locale.ROOT, "L3C%03d", PLACAS.incrementAndGet()), 2020, "Azul",
                jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION'", Long.class),
                jdbc.queryForObject("SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA'", Long.class),
                50000, null, "SERIE123", "MOTOR123", null, null, null, null);
    }

    private long numero(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void assertCodigo(String codigo, Runnable accion) {
        assertEquals(codigo, assertThrows(ErrorRegistroVehiculo.class, accion::run).codigo());
    }
}
