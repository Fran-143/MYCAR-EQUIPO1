package backend.documentos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Almacenamiento local para pruebas USB: persiste en disco para sobrevivir reinicios.
 * Compatible con claves SOAT y licencia.
 */
public class FakeAlmacenamientoDocumentos implements AlmacenamientoDocumentos {
    private final Path root;
    private final Set<String> huerfanos = ConcurrentHashMap.newKeySet();

    public FakeAlmacenamientoDocumentos() {
        this(defaultRoot());
    }

    public FakeAlmacenamientoDocumentos(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear directorio de documentos fake: " + root, ex);
        }
    }

    private static Path defaultRoot() {
        String override = System.getenv("MYCAR_FAKE_DOCS_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "MyCar", "desarrollo-local", "documentos-fake");
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "mycar-documentos-fake");
    }

    @Override
    public PresignUpload generarPresignUpload(String objectKey, String contentType, long maxBytes) {
        exigirClave(objectKey);
        return new PresignUpload(
                "fake://upload/" + objectKey,
                Map.of("Content-Type", contentType == null ? "application/octet-stream" : contentType),
                Instant.now().plusSeconds(600),
                objectKey);
    }

    @Override
    public PresignDownload generarPresignDownload(String objectKey) {
        if (!confirmarExistencia(objectKey)) {
            throw new ErrorAlmacenamiento("OBJETO_NO_ENCONTRADO", "El documento no está disponible.");
        }
        return new PresignDownload(
                "fake://download/" + objectKey,
                Map.of(),
                Instant.now().plusSeconds(300),
                objectKey);
    }

    @Override
    public boolean confirmarExistencia(String objectKey) {
        Path file = archivo(objectKey);
        return Files.isRegularFile(file);
    }

    @Override
    public void guardar(String objectKey, byte[] contenido, String contentType) {
        exigirClave(objectKey);
        Path file = archivo(objectKey);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, contenido == null ? new byte[0] : contenido);
            huerfanos.remove(objectKey);
        } catch (IOException ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo guardar el documento.");
        }
    }

    @Override
    public byte[] leer(String objectKey) {
        Path file = archivo(objectKey);
        if (!Files.isRegularFile(file)) {
            throw new ErrorAlmacenamiento("OBJETO_NO_ENCONTRADO", "El documento no está disponible.");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new ErrorAlmacenamiento("STORAGE_NO_DISPONIBLE", "No se pudo leer el documento.");
        }
    }

    @Override
    public void borrarLogicoHuerfano(String objectKey) {
        Path file = archivo(objectKey);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best-effort
        }
        huerfanos.add(objectKey);
    }

    /** Simula que el cliente completó la subida presignada. */
    public void simularSubida(String objectKey) {
        guardar(objectKey, new byte[0], "application/octet-stream");
    }

    public Set<String> objetosPresentes() {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            return Set.of();
        }
    }

    private Path archivo(String objectKey) {
        String safe = objectKey.replace('\\', '/');
        if (safe.contains("..")) {
            throw new ErrorAlmacenamiento("DATOS_INVALIDOS", "Ruta de objeto inválida.");
        }
        return root.resolve(safe).normalize();
    }

    private static void exigirClave(String objectKey) {
        try {
            RutasDocumentoSoat.exigirSegmentosValidos(objectKey);
            return;
        } catch (RuntimeException ignored) {
            // puede ser ruta de licencia / tive / soporte / fotos
        }
        try {
            RutasDocumentoLicencia.exigirSegmentosValidos(objectKey);
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            RutasDocumentoTive.exigirSegmentosValidos(objectKey);
            return;
        } catch (RuntimeException ignored) {
        }
        try {
            RutasDocumentoSoporte.exigirSegmentosValidos(objectKey);
            return;
        } catch (RuntimeException ignored) {
        }
        backend.fotos.RutasFotoPersonalizada.exigirClaveValida(objectKey);
    }
}
