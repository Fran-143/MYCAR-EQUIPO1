package backend.fotos;

import backend.vehiculos.domain.ErrorRegistroVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FotoVehiculoServiceTest {
    private JdbcTemplate jdbc;
    private FotoVehiculoService servicio;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:fotos_veh_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE SCHEMA IF NOT EXISTS mycar;
                CREATE TABLE mycar.vehiculo (
                  id_vehiculo BIGINT PRIMARY KEY,
                  id_usuario BIGINT NOT NULL,
                  estado_vehiculo VARCHAR(20) NOT NULL,
                  foto_storage_object_key TEXT,
                  foto_content_type TEXT,
                  foto_tamano_bytes BIGINT,
                  foto_actualizada_en TIMESTAMP
                );
                """);
        jdbc.update("INSERT INTO mycar.vehiculo(id_vehiculo, id_usuario, estado_vehiculo) VALUES (1, 7, 'ACTIVO')");
        jdbc.update("INSERT INTO mycar.vehiculo(id_vehiculo, id_usuario, estado_vehiculo) VALUES (2, 7, 'INACTIVO')");
        jdbc.update("INSERT INTO mycar.vehiculo(id_vehiculo, id_usuario, estado_vehiculo) VALUES (3, 99, 'ACTIVO')");
        var root = Files.createTempDirectory("mycar-fotos-veh");
        servicio = new FotoVehiculoService(jdbc, new AlmacenamientoFotos(
                new backend.documentos.FakeAlmacenamientoDocumentos(root)));
    }

    @Test
    void activoPuedeRegistrarYLeer() throws Exception {
        servicio.registrar(7L, 1L, imagen(), "image/jpeg");
        assertTrue(servicio.leerBytes(7L, 1L).length > 0);
    }

    @Test
    void inactivoPuedeLeerPeroNoMutar() throws Exception {
        jdbc.update("""
                UPDATE mycar.vehiculo SET estado_vehiculo='ACTIVO' WHERE id_vehiculo=2
                """);
        servicio.registrar(7L, 2L, imagen(), "image/jpeg");
        jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=2");
        assertTrue(servicio.leerBytes(7L, 2L).length > 0);
        assertThrows(ErrorRegistroVehiculo.class, () -> servicio.actualizar(7L, 2L, imagen(), "image/jpeg"));
        assertThrows(ErrorRegistroVehiculo.class, () -> servicio.eliminar(7L, 2L));
        assertThrows(ErrorRegistroVehiculo.class, () -> servicio.registrar(7L, 2L, imagen(), "image/png"));
    }

    @Test
    void ajenoDenegadoComoNoEncontrado() {
        assertThrows(ErrorRegistroVehiculo.class, () -> servicio.leerBytes(7L, 3L));
        assertThrows(ErrorRegistroVehiculo.class, () -> servicio.registrar(7L, 3L, new byte[]{1}, "image/jpeg"));
    }

    private static byte[] imagen() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
