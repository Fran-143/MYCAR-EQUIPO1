package backend.fotos;

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

class FotoPerfilServiceTest {
    private JdbcTemplate jdbc;
    private AlmacenamientoFotos almacenamiento;
    private FotoPerfilService servicio;

    @BeforeEach
    void setUp() throws Exception {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:fotos_perfil_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE SCHEMA IF NOT EXISTS mycar;
                CREATE TABLE mycar.usuario (
                  id_usuario BIGINT PRIMARY KEY,
                  estado_cuenta VARCHAR(20),
                  foto_storage_object_key TEXT,
                  foto_content_type TEXT,
                  foto_tamano_bytes BIGINT,
                  foto_actualizada_en TIMESTAMP
                );
                """);
        jdbc.update("INSERT INTO mycar.usuario(id_usuario, estado_cuenta) VALUES (7, 'ACTIVA')");
        var root = Files.createTempDirectory("mycar-fotos-test");
        almacenamiento = new AlmacenamientoFotos(new backend.documentos.FakeAlmacenamientoDocumentos(root));
        servicio = new FotoPerfilService(jdbc, almacenamiento);
    }

    @Test
    void registrarYLeerJpeg() throws Exception {
        servicio.registrar(7L, imagen("jpg"), "image/jpeg");
        assertTrue(almacenamiento.confirmarExistencia(
                jdbc.queryForObject("SELECT foto_storage_object_key FROM mycar.usuario WHERE id_usuario=7", String.class)));
        byte[] bytes = servicio.leerBytes(7L);
        assertTrue(bytes.length > 0);
        assertEquals("image/jpeg", servicio.metadatos(7L).contentType());
    }

    @Test
    void actualizarConservaReferenciaNuevaYBorraAnterior() throws Exception {
        servicio.registrar(7L, imagen("jpg"), "image/jpeg");
        String anterior = jdbc.queryForObject(
                "SELECT foto_storage_object_key FROM mycar.usuario WHERE id_usuario=7", String.class);
        servicio.actualizar(7L, imagen("png"), "image/png");
        String actual = jdbc.queryForObject(
                "SELECT foto_storage_object_key FROM mycar.usuario WHERE id_usuario=7", String.class);
        assertNotEquals(anterior, actual);
        assertFalse(almacenamiento.confirmarExistencia(anterior));
        assertTrue(almacenamiento.confirmarExistencia(actual));
    }

    @Test
    void eliminarQuitaReferencia() throws Exception {
        servicio.registrar(7L, imagen("jpg"), "image/jpeg");
        String clave = jdbc.queryForObject(
                "SELECT foto_storage_object_key FROM mycar.usuario WHERE id_usuario=7", String.class);
        servicio.eliminar(7L);
        assertNull(jdbc.queryForObject(
                "SELECT foto_storage_object_key FROM mycar.usuario WHERE id_usuario=7", String.class));
        assertFalse(almacenamiento.confirmarExistencia(clave));
        assertThrows(FotoException.class, () -> servicio.leerBytes(7L));
    }

    @Test
    void registrarDosVecesEsConflicto() throws Exception {
        servicio.registrar(7L, imagen("jpg"), "image/jpeg");
        assertThrows(FotoException.class, () -> servicio.registrar(7L, imagen("jpg"), "image/jpeg"));
    }

    private static byte[] imagen(String formato) throws Exception {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 24, 24);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, formato, out);
        return out.toByteArray();
    }
}
