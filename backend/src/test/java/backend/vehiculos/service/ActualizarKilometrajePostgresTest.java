package backend.vehiculos.service;

import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dto.DatosActualizarKilometraje;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mycar.rf08.tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=optional:file:./target/rf08-sin-env.properties",
        "spring.flyway.enabled=false"
})
class ActualizarKilometrajePostgresTest {
    private static final AtomicInteger PLACAS = new AtomicInteger(800);
    @Autowired ActualizarKilometrajeService rf14;
    @Autowired RegistroVehiculoService registro;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;

    @DynamicPropertySource static void destino(DynamicPropertyRegistry propiedades) {
        String url = System.getenv("MYCAR_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/mycar_mvp_test_[a-z0-9_]+")) {
            throw new IllegalStateException("RF14 exige BD desechable");
        }
        propiedades.add("spring.datasource.url", () -> url);
        propiedades.add("spring.datasource.username", () -> System.getenv("MYCAR_TEST_USER"));
        propiedades.add("spring.datasource.password", () -> System.getenv("MYCAR_TEST_PASSWORD"));
    }

    @Test void actualizaKmValidoEIncrementaVersion() {
        long usuario = cuenta(true);
        var alta = registro.registrar(usuario, datos());
        UUID op = UUID.randomUUID();
        var ctx = rf14.consultarActual(usuario, alta.idVehiculo());
        var resultado = rf14.registrar(usuario, alta.idVehiculo(), new DatosActualizarKilometraje(
                op, ctx.kmActual() + 500, ctx.idLectura(), ctx.versionKm()));
        assertEquals(ctx.kmActual() + 500, resultado.kmActual());
        assertEquals(1, jdbc.queryForObject("SELECT version_km FROM mycar.vehiculo WHERE id_vehiculo=?", Integer.class, alta.idVehiculo()));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", Long.class, alta.idVehiculo()));
    }

    @Test void rechazaKmIgualYVehiculoAjeno() {
        long uno = cuenta(true), dos = cuenta(true);
        var propio = registro.registrar(uno, datos());
        var ajeno = registro.registrar(dos, datos());
        var ctx = rf14.consultarActual(uno, propio.idVehiculo());
        assertEquals("KM_NO_SUPERIOR", assertThrows(ErrorRegistroVehiculo.class, () -> rf14.registrar(
                uno, propio.idVehiculo(), new DatosActualizarKilometraje(
                        UUID.randomUUID(), ctx.kmActual(), ctx.idLectura(), ctx.versionKm()))).codigo());
        assertEquals("VEHICULO_NO_ENCONTRADO", assertThrows(ErrorRegistroVehiculo.class, () -> rf14.registrar(
                dos, propio.idVehiculo(), new DatosActualizarKilometraje(
                        UUID.randomUUID(), ctx.kmActual() + 1, ctx.idLectura(), ctx.versionKm()))).codigo());
        assertNotEquals(ajeno.idVehiculo(), propio.idVehiculo());
    }

    @Test void idempotenciaRepiteMismaRespuesta() {
        long usuario = cuenta(true);
        var alta = registro.registrar(usuario, datos());
        var ctx = rf14.consultarActual(usuario, alta.idVehiculo());
        UUID op = UUID.randomUUID();
        var solicitud = new DatosActualizarKilometraje(op, ctx.kmActual() + 100, ctx.idLectura(), ctx.versionKm());
        var primero = rf14.registrar(usuario, alta.idVehiculo(), solicitud);
        var segundo = rf14.registrar(usuario, alta.idVehiculo(), solicitud);
        assertEquals(primero, segundo);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?", Long.class, alta.idVehiculo()));
    }

    private long cuenta(boolean free) {
        return new TransactionTemplate(transacciones).execute(s -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,
                        consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version)
                    VALUES (?,repeat('x',60),'RF14','Test',?,clock_timestamp(),'v1') RETURNING id_usuario
                    """, Long.class, UUID.randomUUID() + "@example.test", LocalDate.now(ZoneId.of("America/Lima")).minusYears(30));
            if (free) {
                jdbc.update("INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) SELECT ?,id_plan,'ACTIVA' FROM mycar.plan WHERE codigo='FREE' AND activo", id);
            }
            jdbc.update("INSERT INTO mycar.preferencia_notificacion(id_usuario) VALUES (?)", id);
            jdbc.update("INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario) VALUES (?)", id);
            return id;
        });
    }

    private DatosRegistroVehiculo datos() {
        return new DatosRegistroVehiculo(UUID.randomUUID(), "Toyota", "Corolla", "PARTICULAR",
                String.format(Locale.ROOT, "M8K%03d", PLACAS.incrementAndGet()), 2020, "Azul",
                jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION'", Long.class),
                jdbc.queryForObject("SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA'", Long.class),
                50000, null, null, null, null, null, null, null);
    }
}
