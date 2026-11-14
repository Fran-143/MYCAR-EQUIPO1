package backend.soat.service;

import backend.documentos.AlmacenamientoDocumentos;
import backend.documentos.FakeAlmacenamientoDocumentos;
import backend.soat.api.dto.ContratoSoat;
import backend.soat.dao.SoatJdbcDao;
import backend.vehiculos.dao.OperacionVehiculoDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RF31-EA13: fallo transaccional al renovar → ROLLBACK; SOAT anterior sigue ACTUAL.
 * Activa con MYCAR_V27_JDBC_URL (PostgreSQL aislado; nunca Neon prod).
 *
 * Compensación documental: @Transactional no revierte archivos en Supabase.
 * La renovación solo enlaza objectKey tras confirmar existencia; el documento del
 * SOAT anterior permanece referenciado en la fila ACTUAL tras el rollback.
 */
@EnabledIfEnvironmentVariable(named = "MYCAR_V27_JDBC_URL", matches = "jdbc:postgresql://.+")
class Rf31Ea13RenovacionRollbackIT {

    @Test
    void renovacionConFalloSqlConservaSoatAnteriorSinAlertaNueva() {
        String url = System.getenv("MYCAR_V27_JDBC_URL");
        assertFalse(url.contains("neon"), "Prohibido Neon producción");
        String user = System.getenv().getOrDefault("MYCAR_V27_USER", "mycar");
        String pass = System.getenv().getOrDefault("MYCAR_V27_PASSWORD", "mycar");

        var ds = new DriverManagerDataSource(url, user, pass);
        var jdbc = new JdbcTemplate(ds);
        var txm = new DataSourceTransactionManager(ds);
        var json = new ObjectMapper();
        var dao = new SoatJdbcDao(jdbc);
        var operaciones = new OperacionVehiculoDao(jdbc);
        AlmacenamientoDocumentos storage = new FakeAlmacenamientoDocumentos();

        long usuario = jdbc.queryForObject("""
                INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,
                    consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version,estado_cuenta)
                VALUES (?, repeat('x',60), 'EA13', 'Test', CURRENT_DATE - interval '30 years',
                    clock_timestamp(), '1.0', 'ACTIVA')
                RETURNING id_usuario
                """, Long.class, "ea13_" + System.nanoTime() + "@test.local");

        String placa = "E" + (char) ('A' + (int) (System.nanoTime() % 26))
                + "B" + String.format("%03d", (int) (System.nanoTime() % 1000));
        long vehiculo = jdbc.queryForObject("""
                INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,
                    id_tipo_motor,id_combustible)
                VALUES (?,?,'Toyota','Yaris','PARTICULAR',2018,'Azul',
                    (SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION'),
                    (SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA'))
                RETURNING id_vehiculo
                """, Long.class, usuario, placa);

        LocalDate ini = LocalDate.of(2025, 1, 1);
        LocalDate venc = LocalDate.of(2026, 1, 1);
        long idAnterior = jdbc.queryForObject("""
                INSERT INTO mycar.soat(id_vehiculo, aseguradora, numero_poliza, fecha_inicio, fecha_vencimiento,
                    vigencia, recordatorio_activo, storage_object_key, content_type, tamano_bytes, nombre_original)
                VALUES (?, 'Rimac', 'POL-EA13', ?, ?, 'ACTUAL', false, 'soat/1/1/temp/old.pdf',
                    'application/pdf', 10, 'old.pdf')
                RETURNING id_soat
                """, Long.class, vehiculo, ini, venc);

        SoatJdbcDao spyDao = spy(dao);
        doThrow(new DataIntegrityViolationException("RF31-EA13 simulated"))
                .when(spyDao).insertar(any());

        @SuppressWarnings("unchecked")
        ObjectProvider<backend.notificaciones.service.NotificacionInternaService> notif =
                mock(ObjectProvider.class);
        when(notif.getIfAvailable()).thenReturn(null);

        SoatService servicio = transactional(new SoatService(spyDao, operaciones, storage, json, notif), txm);

        var req = new ContratoSoat.RenovarRequest(
                UUID.randomUUID(),
                idAnterior,
                "Pacifico",
                "POL-EA13-NEW",
                venc.plusDays(1),
                venc.plusDays(366),
                null,
                null,
                BigDecimal.ONE,
                null);

        assertThrows(DataIntegrityViolationException.class, () -> servicio.renovar(usuario, vehiculo, req));

        String vigencia = jdbc.queryForObject(
                "SELECT vigencia FROM mycar.soat WHERE id_soat=?", String.class, idAnterior);
        assertEquals("ACTUAL", vigencia, "SOAT anterior debe seguir ACTUAL tras rollback");

        Integer nuevos = jdbc.queryForObject(
                "SELECT count(*)::int FROM mycar.soat WHERE id_vehiculo=? AND vigencia='ACTUAL' AND id_soat<>?",
                Integer.class, vehiculo, idAnterior);
        assertEquals(0, nuevos);

        Integer historicos = jdbc.queryForObject(
                "SELECT count(*)::int FROM mycar.soat WHERE id_vehiculo=? AND vigencia='HISTORICO'",
                Integer.class, vehiculo);
        assertEquals(0, historicos);

        Integer alertas = jdbc.queryForObject(
                "SELECT count(*)::int FROM mycar.alerta WHERE id_vehiculo=? AND categoria='SOAT'",
                Integer.class, vehiculo);
        assertEquals(0, alertas);

        String docKey = jdbc.queryForObject(
                "SELECT storage_object_key FROM mycar.soat WHERE id_soat=?", String.class, idAnterior);
        assertEquals("soat/1/1/temp/old.pdf", docKey);
    }

    private static SoatService transactional(SoatService target, DataSourceTransactionManager txm) {
        ProxyFactory pf = new ProxyFactory(target);
        pf.addAdvice(new TransactionInterceptor(txm, new AnnotationTransactionAttributeSource()));
        return (SoatService) pf.getProxy();
    }
}
