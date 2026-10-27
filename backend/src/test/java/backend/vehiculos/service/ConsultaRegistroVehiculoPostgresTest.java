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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:./target/rf08-sin-env.properties",
        "spring.flyway.enabled=false"
})
class ConsultaRegistroVehiculoPostgresTest {
    private static final AtomicInteger PLACAS = new AtomicInteger(100);
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

    @Test void catalogosSeConsultanDesdePostgresSinInsertarOperaciones() {
        long usuario = cuenta(true);
        var catalogos = consultas.consultarCatalogos(usuario);
        assertTrue(catalogos.usos().stream().anyMatch(u -> "PARTICULAR".equals(u.codigo())));
        assertTrue(catalogos.tiposMotor().stream().anyMatch(m -> "ELECTRICO".equals(m.codigo()) && m.esElectrico()));
        assertFalse(catalogos.combustibles().isEmpty());
        assertFalse(catalogos.tiposAceite().isEmpty());
        assertEquals(0, numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=?", usuario));
    }

    @Test void consultarCupoDisponibleNoReservaNiCreaVehiculo() {
        long usuario = cuenta(true);
        var cupo = consultas.consultarCupo(usuario);
        assertEquals("FREE", cupo.plan());
        assertEquals(1, cupo.limite());
        assertEquals(0, cupo.registrados());
        assertTrue(cupo.puedeRegistrar());
        assertEquals(cupo, consultas.consultarCupo(usuario));
        assertEquals(0, numero("SELECT count(*) FROM mycar.vehiculo WHERE id_usuario=?", usuario));
        assertEquals(0, numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=?", usuario));
    }

    @Test void cupoIncluyeVehiculosInactivos() {
        long usuario = cuenta(true);
        var resultado = registro.registrar(usuario, datos());
        jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", resultado.idVehiculo());
        var cupo = consultas.consultarCupo(usuario);
        assertEquals(1, cupo.registrados());
        assertFalse(cupo.puedeRegistrar());
    }

    @Test void consultarSinPlanNoLoCreaNiFingeDisponibilidad() {
        long usuario = cuenta(false);
        assertCodigo("PLAN_FREE_NO_DISPONIBLE", () -> consultas.consultarCupo(usuario));
        assertEquals(0, numero("SELECT count(*) FROM mycar.suscripcion WHERE id_usuario=?", usuario));
    }

    @Test void consultaConfirmadaDevuelveResultadoOriginalAunqueElPlanCambie() {
        long usuario = cuenta(true);
        var datos = datos();
        var original = registro.registrar(usuario, datos);
        jdbc.update("UPDATE mycar.suscripcion SET estado='VENCIDA' WHERE id_usuario=?", usuario);
        var consulta = consultas.consultarOperacion(usuario, datos.idOperacion());
        assertEquals("CONFIRMADA", consulta.estado());
        assertEquals(original, consulta.resultado());
        assertEquals(consulta, consultas.consultarOperacion(usuario, datos.idOperacion()));
        assertEquals(1, numero("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_operacion=?", datos.idOperacion()));
    }

    @Test void operacionAjenaYAusenteNoExponenResultados() {
        long uno = cuenta(true), dos = cuenta(true);
        var datos = datos();
        registro.registrar(uno, datos);
        assertCodigo("OPERACION_NO_ENCONTRADA", () -> consultas.consultarOperacion(dos, datos.idOperacion()));
        assertCodigo("OPERACION_NO_ENCONTRADA", () -> consultas.consultarOperacion(dos, UUID.randomUUID()));
        assertEquals(0, numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=?", dos));
    }

    @Test void operacionDelMismoUsuarioPeroOtroTipoTampocoSeExpone() {
        long usuario = cuenta(true);
        UUID op = UUID.randomUUID();
        new TransactionTemplate(transacciones).execute(s -> operaciones.reservar(op, usuario, "ACTUALIZAR_KM", "a".repeat(64)));
        assertCodigo("OPERACION_NO_ENCONTRADA", () -> consultas.consultarOperacion(usuario, op));
        assertEquals("ACTUALIZAR_KM", jdbc.queryForObject("SELECT tipo_operacion FROM mycar.operacion_idempotente WHERE id_operacion=?", String.class, op));
    }

    @Test void consultarEnProcesoNoConfirmaNiCreaFilas() {
        long usuario = cuenta(true);
        var datos = datos();
        new TransactionTemplate(transacciones).execute(s -> operaciones.reservar(datos.idOperacion(), usuario, "REGISTRAR_VEHICULO", huellas.calcular(datos)));
        var consulta = consultas.consultarOperacion(usuario, datos.idOperacion());
        assertEquals("EN_PROCESO", consulta.estado());
        assertNull(consulta.resultado());
        assertEquals(consulta, consultas.consultarOperacion(usuario, datos.idOperacion()));
        assertEquals(0, numero("SELECT count(*) FROM mycar.vehiculo WHERE id_usuario=?", usuario));
        assertEquals("EN_PROCESO", jdbc.queryForObject("SELECT estado FROM mycar.operacion_idempotente WHERE id_operacion=?", String.class, datos.idOperacion()));
    }

    @Test void cuentaSuspendidaNoAccedeACatalogosCupoNiOperaciones() {
        long usuario = cuenta(true);
        var datos = datos();
        registro.registrar(usuario, datos);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA' WHERE id_usuario=?", usuario);
        assertCodigo("CUENTA_NO_AUTORIZADA", () -> consultas.consultarCatalogos(usuario));
        assertCodigo("CUENTA_NO_AUTORIZADA", () -> consultas.consultarCupo(usuario));
        assertCodigo("CUENTA_NO_AUTORIZADA", () -> consultas.consultarOperacion(usuario, datos.idOperacion()));
    }

    @Test void lecturaNoVeOperacionSinCommitYNoInterpretaAusenciaComoRollback() throws Exception {
        long usuario = cuenta(true);
        UUID op = UUID.randomUUID();
        try (var conexion = fuente.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                try (var p = conexion.prepareStatement("INSERT INTO mycar.operacion_idempotente(id_operacion,id_usuario,tipo_operacion,hash_solicitud) VALUES (?,?,'REGISTRAR_VEHICULO',?)")) {
                    p.setObject(1, op); p.setLong(2, usuario); p.setString(3, "a".repeat(64));
                    p.executeUpdate();
                }
                assertCodigo("OPERACION_NO_ENCONTRADA", () -> consultas.consultarOperacion(usuario, op));
            } finally { conexion.rollback(); }
        }
        assertEquals(0, numero("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_operacion=?", op));
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
                String.format(Locale.ROOT, "K2B%03d", PLACAS.incrementAndGet()), 2020, "Azul",
                jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION'", Long.class),
                jdbc.queryForObject("SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA'", Long.class),
                50000, null, "SERIE123", "MOTOR123", null, null, null, null);
    }

    private long numero(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }
    private void assertCodigo(String codigo, Runnable accion) {
        assertEquals(codigo, assertThrows(ErrorRegistroVehiculo.class, accion::run).codigo());
    }
}
