package backend.vehiculos.service;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.dao.RegistroInicialVehiculoDao;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import backend.vehiculos.dto.ResultadoRegistroVehiculo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Prueba servicios/proxies/transacciones reales; exclusivamente en PostgreSQL desechable. */
@EnabledIfSystemProperty(named = "mycar.rf08.tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:./target/rf08-sin-env.properties",
        "spring.flyway.enabled=false"
})
class RegistroVehiculoServicePostgresTest {
    private static final AtomicInteger PLACAS = new AtomicInteger(100);
    @Autowired RegistroVehiculoService servicio;
    @Autowired OperacionVehiculoDao operaciones;
    @Autowired HuellaRegistroVehiculo huellas;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transacciones;
    @MockitoSpyBean RegistroInicialVehiculoDao inicial;

    @DynamicPropertySource
    static void destino(DynamicPropertyRegistry registro) {
        String url = System.getenv("MYCAR_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/mycar_mvp_test_[a-z0-9_]+")) {
            throw new IllegalStateException("RF08 solo permite una BD temporal mycar_mvp_test_*");
        }
        registro.add("spring.datasource.url", () -> url);
        registro.add("spring.datasource.username", () -> System.getenv("MYCAR_TEST_USER"));
        registro.add("spring.datasource.password", () -> System.getenv("MYCAR_TEST_PASSWORD"));
    }

    @AfterEach void restablecerEspia() {
        RegistroInicialVehiculoDao objetivo = AopTestUtils.getUltimateTargetObject(inicial);
        reset(objetivo);
    }

    @Test void altaGuardaVehiculoLecturaConfiguracionYRespuesta() throws Exception {
        long usuario = cuenta(true);
        var datos = datos();
        var resultado = servicio.registrar(usuario, datos);
        assertTrue(resultado.idVehiculo() > 0);
        assertTrue(resultado.principal());
        assertEquals(datos.placa(), resultado.placa());
        assertEquals(datos.kilometrajeInicial().intValue(), resultado.kilometrajeActual());
        assertEquals(0, resultado.versionKm());
        assertNotNull(resultado.fechaLectura());
        assertEquals(1, contar("SELECT count(*) FROM mycar.vehiculo WHERE id_vehiculo=? AND id_usuario=? AND es_principal AND estado_vehiculo='ACTIVO'", resultado.idVehiculo(), usuario));
        assertEquals(1, contar("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_lectura_km=? AND id_vehiculo=? AND origen='INICIAL' AND id_operacion=? AND valor_km=?", resultado.idLecturaKm(), resultado.idVehiculo(), datos.idOperacion(), datos.kilometrajeInicial()));
        assertEquals(1, contar("""
                SELECT count(*) FROM mycar.config_recordatorio_km r
                JOIN mycar.lectura_kilometraje l ON l.id_vehiculo=r.id_vehiculo
                WHERE r.id_vehiculo=? AND r.frecuencia='INTERDIARIO' AND r.estado='PAUSADO'
                  AND r.fecha_base_utc=l.fecha_lectura_utc
                  AND r.proximo_envio_utc IS NULL AND r.ultimo_envio_utc IS NULL
                """, resultado.idVehiculo()));
        String almacenada = jdbc.queryForObject("SELECT respuesta_json::text FROM mycar.operacion_idempotente WHERE id_operacion=? AND estado='CONFIRMADA'", String.class, datos.idOperacion());
        assertEquals(resultado, json.readValue(almacenada, ResultadoRegistroVehiculo.class));
        assertEquals("PAUSADO", jdbc.queryForObject("SELECT estado_recordatorios FROM mycar.config_recordatorio_km_usuario WHERE id_usuario=?", String.class, usuario));
        assertEquals(0, contar("SELECT count(*) FROM mycar.alerta WHERE id_vehiculo=?", resultado.idVehiculo()));
    }

    @Test void reintentoConfirmadoRecuperaResultadoAntesDeRevalidarPlanOCupo() {
        long usuario = cuenta(true);
        var datos = datos();
        var original = servicio.registrar(usuario, datos);
        jdbc.update("UPDATE mycar.suscripcion SET estado='VENCIDA' WHERE id_usuario=?", usuario);
        assertEquals(original, servicio.registrar(usuario, datos));
        assertEquals(1, vehiculos(usuario));
        assertEquals(1, contar("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_operacion=?", datos.idOperacion()));
    }

    @Test void mismaClaveConContenidoDistintoProduceConflicto() {
        long usuario = cuenta(true);
        var datos = datos();
        var original = servicio.registrar(usuario, datos);
        var distinto = datos(datos.idOperacion(), datos.placa(), null, "Rojo", "COMBUSTION");
        assertCodigo("CONFLICTO_IDEMPOTENCIA", () -> servicio.registrar(usuario, distinto));
        assertEquals(original, servicio.registrar(usuario, datos));
        assertEquals(1, vehiculos(usuario));
    }

    @Test void otraCuentaNoRecuperaRespuestaAjena() {
        long propietario = cuenta(true), otro = cuenta(true);
        var datos = datos();
        servicio.registrar(propietario, datos);
        assertCodigo("CONFLICTO_IDEMPOTENCIA", () -> servicio.registrar(otro, datos));
        assertEquals(0, vehiculos(otro));
        assertEquals(propietario, contar("SELECT id_usuario FROM mycar.operacion_idempotente WHERE id_operacion=?", datos.idOperacion()));
    }

    @Test void nuevaOperacionRespetaLimiteFreeYRevierteLaReserva() {
        long usuario = cuenta(true);
        servicio.registrar(usuario, datos());
        var segundo = datos();
        assertCodigo("LIMITE_FREE", () -> servicio.registrar(usuario, segundo));
        assertEquals(1, vehiculos(usuario));
        assertSinOperacion(segundo);
    }

    @Test void vehiculoInactivoTambienOcupaCupoFree() {
        long usuario = cuenta(true);
        var primero = servicio.registrar(usuario, datos());
        jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?", primero.idVehiculo());
        var segundo = datos();
        assertCodigo("LIMITE_FREE", () -> servicio.registrar(usuario, segundo));
        assertEquals(1, vehiculos(usuario));
        assertSinOperacion(segundo);
    }

    @Test void placaEsUnicaEntrePropietarios() {
        long uno = cuenta(true), dos = cuenta(true);
        var primero = datos();
        servicio.registrar(uno, primero);
        var segundo = datos(UUID.randomUUID(), primero.placa(), null, "Azul", "COMBUSTION");
        assertCodigo("PLACA_DUPLICADA", () -> servicio.registrar(dos, segundo));
        assertEquals(0, vehiculos(dos));
        assertSinOperacion(segundo);
    }

    @Test void vinEsUnicoEntrePropietarios() {
        long uno = cuenta(true), dos = cuenta(true);
        String vin = "1HGCM82633A004352";
        servicio.registrar(uno, datos(UUID.randomUUID(), placa(), vin, "Azul", "COMBUSTION"));
        var segundo = datos(UUID.randomUUID(), placa(), vin, "Azul", "COMBUSTION");
        assertCodigo("VIN_DUPLICADO", () -> servicio.registrar(dos, segundo));
        assertEquals(0, vehiculos(dos));
        assertSinOperacion(segundo);
    }

    @Test void identificacionInvalidaNoDejaVehiculoNiReserva() {
        long usuario = cuenta(true);
        var datos = datos(UUID.randomUUID(), placa(), "VININVALIDO", "Azul", "COMBUSTION");
        var error = assertCodigo("DATOS_INVALIDOS", () -> servicio.registrar(usuario, datos));
        assertTrue(error.campos().contains("numeroVin"));
        assertEquals(0, vehiculos(usuario));
        assertSinOperacion(datos);
    }

    @Test void faltaIdOperacionSeRechazaSinEscrituras() {
        long usuario = cuenta(true);
        var datos = datos(null, placa(), null, "Azul", "COMBUSTION");
        var error = assertCodigo("DATOS_INVALIDOS", () -> servicio.registrar(usuario, datos));
        assertTrue(error.campos().contains("idOperacion"));
        assertEquals(0, vehiculos(usuario));
    }

    @Test void cuentaSuspendidaNoPuedeNiRecuperarUnaOperacion() {
        long usuario = cuenta(true);
        var datos = datos();
        servicio.registrar(usuario, datos);
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA' WHERE id_usuario=?", usuario);
        assertCodigo("CUENTA_NO_AUTORIZADA", () -> servicio.registrar(usuario, datos));
        assertEquals(1, vehiculos(usuario));
    }

    @Test void noSeInventaUnaSuscripcionFreeFaltante() {
        long usuario = cuenta(false);
        var datos = datos();
        assertCodigo("PLAN_FREE_NO_DISPONIBLE", () -> servicio.registrar(usuario, datos));
        assertEquals(0, vehiculos(usuario));
        assertSinOperacion(datos);
        assertEquals(0, contar("SELECT count(*) FROM mycar.suscripcion WHERE id_usuario=?", usuario));
    }

    @Test void electricoSeGuardaSinCombustibleAceiteNiNumeroMotor() {
        long usuario = cuenta(true);
        var resultado = servicio.registrar(usuario, datos(UUID.randomUUID(), placa(), null, "Azul", "ELECTRICO"));
        assertEquals(1, contar("SELECT count(*) FROM mycar.vehiculo WHERE id_vehiculo=? AND id_combustible IS NULL AND id_tipo_aceite IS NULL AND numero_motor IS NULL", resultado.idVehiculo()));
    }

    @Test void falloDespuesDeLecturaYConfiguracionRevierteTodaElAlta() {
        long usuario = cuenta(true);
        var datos = datos();
        RegistroInicialVehiculoDao objetivo = AopTestUtils.getUltimateTargetObject(inicial);
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            throw new DataAccessResourceFailureException("Fallo simulado después de persistir lectura y configuración");
        }).when(objetivo).registrar(anyLong(), anyInt(), eq(datos.idOperacion()));
        assertThrows(DataAccessResourceFailureException.class, () -> servicio.registrar(usuario, datos));
        assertEquals(0, vehiculos(usuario));
        assertSinOperacion(datos);
        assertEquals(0, contar("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_operacion=?", datos.idOperacion()));
        assertEquals(0, contar("SELECT count(*) FROM mycar.config_recordatorio_km r JOIN mycar.vehiculo v ON v.id_vehiculo=r.id_vehiculo WHERE v.id_usuario=?", usuario));
    }

    @Test void dosAltasConcurrentesDeLaMismaCuentaSoloConfirmanUna() throws Exception {
        long usuario = cuenta(true);
        var resultados = competir(usuario, datos(), datos());
        assertEquals(1, resultados.stream().filter(ResultadoRegistroVehiculo.class::isInstance).count());
        assertEquals(1, resultados.stream().filter("LIMITE_FREE"::equals).count());
        assertEquals(1, vehiculos(usuario));
        assertEquals(1, contar("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario=? AND estado='CONFIRMADA'", usuario));
    }

    @Test void dosReintentosConcurrentesDeLaMismaOperacionDevuelvenElMismoResultado() throws Exception {
        long usuario = cuenta(true);
        var datos = datos();
        var resultados = competir(usuario, datos, datos);
        assertInstanceOf(ResultadoRegistroVehiculo.class, resultados.getFirst());
        assertEquals(resultados.getFirst(), resultados.getLast());
        assertEquals(1, vehiculos(usuario));
        assertEquals(1, contar("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_operacion=?", datos.idOperacion()));
    }

    @Test void placaConcurrenteEntrePropietariosConfirmaSoloUnVehiculo() throws Exception {
        long uno = cuenta(true), dos = cuenta(true);
        String placa = placa();
        var datosUno = datos(UUID.randomUUID(), placa, null, "Azul", "COMBUSTION");
        var datosDos = datos(UUID.randomUUID(), placa, null, "Azul", "COMBUSTION");
        var resultados = competir(uno, datosUno, dos, datosDos);
        assertEquals(1, resultados.stream().filter(ResultadoRegistroVehiculo.class::isInstance).count());
        assertEquals(1, resultados.stream().filter(r -> Set.of("PLACA_DUPLICADA", "CONFLICTO_REGISTRO").contains(r)).count());
        assertEquals(1, vehiculos(uno) + vehiculos(dos));
        assertEquals(1, contar("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_usuario IN (?,?)", uno, dos));
    }

    @Test void daoDeEscrituraExigeTransaccionSpring() {
        assertThrows(IllegalTransactionStateException.class,
                () -> inicial.registrar(-1, 100, UUID.randomUUID()));
    }

    @Test void operacionPreexistenteEnProcesoNoSePresentaComoExitoNiSeDuplica() {
        long usuario = cuenta(true);
        var datos = datos();
        new TransactionTemplate(transacciones).execute(status -> operaciones.reservar(
                datos.idOperacion(), usuario, "REGISTRAR_VEHICULO", huellas.calcular(datos)));
        assertCodigo("OPERACION_EN_PROCESO", () -> servicio.registrar(usuario, datos));
        assertEquals(0, vehiculos(usuario));
        assertEquals("EN_PROCESO", jdbc.queryForObject("SELECT estado FROM mycar.operacion_idempotente WHERE id_operacion=?", String.class, datos.idOperacion()));
    }

    @Test void mismaClaveDeOtroTipoNoPuedeReutilizarse() {
        long usuario = cuenta(true);
        var datos = datos();
        new TransactionTemplate(transacciones).execute(status -> operaciones.reservar(
                datos.idOperacion(), usuario, "ACTUALIZAR_KM", huellas.calcular(datos)));
        assertCodigo("CONFLICTO_IDEMPOTENCIA", () -> servicio.registrar(usuario, datos));
        assertEquals(0, vehiculos(usuario));
        assertEquals("ACTUALIZAR_KM", jdbc.queryForObject("SELECT tipo_operacion FROM mycar.operacion_idempotente WHERE id_operacion=?", String.class, datos.idOperacion()));
    }

    @Test void huellaEsDeterministaYDistingueCambiosDeContenido() {
        var datos = datos();
        var distinto = datos(datos.idOperacion(), datos.placa(), null, "Rojo", "COMBUSTION");
        String original = huellas.calcular(datos);
        assertTrue(original.matches("[a-f0-9]{64}"));
        assertEquals(original, huellas.calcular(datos));
        assertNotEquals(original, huellas.calcular(distinto));
    }

    private long cuenta(boolean conFree) {
        assertTrue(jdbc.queryForObject("SHOW server_version", String.class).startsWith("17."));
        assertEquals(1, contar("SELECT count(*) FROM mycar.flyway_schema_history WHERE success AND version='5'"));
        return new TransactionTemplate(transacciones).execute(status -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO mycar.usuario (
                        correo, password_hash, nombre, apellido, fecha_nacimiento,
                        consentimiento_privacidad_fecha_utc, consentimiento_privacidad_version
                    ) VALUES (?, repeat('x',60), 'Prueba', 'RF08', ?, clock_timestamp(), 'v1')
                    RETURNING id_usuario
                    """, Long.class, UUID.randomUUID() + "@example.test",
                    LocalDate.now(ZoneId.of("America/Lima")).minusYears(30));
            if (conFree) {
                assertEquals(1, jdbc.update("INSERT INTO mycar.suscripcion(id_usuario,id_plan,estado) SELECT ?,id_plan,'ACTIVA' FROM mycar.plan WHERE codigo='FREE' AND activo", id));
            }
            jdbc.update("INSERT INTO mycar.preferencia_notificacion(id_usuario) VALUES (?)", id);
            jdbc.update("INSERT INTO mycar.config_recordatorio_km_usuario(id_usuario) VALUES (?)", id);
            return id;
        });
    }

    private DatosRegistroVehiculo datos() {
        return datos(UUID.randomUUID(), placa(), null, "Azul", "COMBUSTION");
    }

    private DatosRegistroVehiculo datos(UUID operacion, String placa, String vin, String color, String motor) {
        Long idMotor = jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo=?", Long.class, motor);
        Long combustible = "ELECTRICO".equals(motor) ? null : jdbc.queryForObject("SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA'", Long.class);
        return new DatosRegistroVehiculo(operacion, "Toyota", "Corolla", "PARTICULAR", placa,
                2020, color, idMotor, combustible, 50000, vin, "SERIE123",
                "ELECTRICO".equals(motor) ? null : "MOTOR123", null, null, null, null);
    }

    private static String placa() {
        return String.format(Locale.ROOT, "T1A%03d", PLACAS.incrementAndGet());
    }

    private long contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long vehiculos(long usuario) {
        return contar("SELECT count(*) FROM mycar.vehiculo WHERE id_usuario=?", usuario);
    }

    private void assertSinOperacion(DatosRegistroVehiculo datos) {
        assertEquals(0, contar("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_operacion=?", datos.idOperacion()));
    }

    private ErrorRegistroVehiculo assertCodigo(String codigo, Runnable accion) {
        var error = assertThrows(ErrorRegistroVehiculo.class, accion::run);
        assertEquals(codigo, error.codigo());
        return error;
    }

    private List<Object> competir(long usuario, DatosRegistroVehiculo uno, DatosRegistroVehiculo dos) throws Exception {
        return competir(usuario, uno, usuario, dos);
    }

    private List<Object> competir(long usuarioUno, DatosRegistroVehiculo uno, long usuarioDos, DatosRegistroVehiculo dos) throws Exception {
        var inicio = new CountDownLatch(1);
        var ejecutor = Executors.newFixedThreadPool(2);
        try {
            var primera = ejecutor.submit(() -> intentar(inicio, usuarioUno, uno));
            var segunda = ejecutor.submit(() -> intentar(inicio, usuarioDos, dos));
            inicio.countDown();
            return List.of(primera.get(25, TimeUnit.SECONDS), segunda.get(25, TimeUnit.SECONDS));
        } finally {
            ejecutor.shutdownNow();
            assertTrue(ejecutor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Object intentar(CountDownLatch inicio, long usuario, DatosRegistroVehiculo datos) throws Exception {
        inicio.await();
        try {
            return servicio.registrar(usuario, datos);
        } catch (ErrorRegistroVehiculo error) {
            return error.codigo();
        }
    }
}
