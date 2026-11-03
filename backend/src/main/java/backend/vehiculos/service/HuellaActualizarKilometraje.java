package backend.vehiculos.service;

import backend.vehiculos.dto.DatosActualizarKilometraje;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class HuellaActualizarKilometraje {
    private static final ObjectMapper CANONICO = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    public String calcular(long idVehiculo, DatosActualizarKilometraje datos) {
        try {
            var payload = new HuellaPayload(
                    idVehiculo,
                    datos.idLecturaBase(),
                    datos.kmNuevo(),
                    datos.versionBase());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("MYCAR:RF14:v1\n".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(CANONICO.writeValueAsBytes(payload)));
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo calcular la huella RF14", ex);
        }
    }

    private record HuellaPayload(long idVehiculo, Long idLecturaBase, Integer kmNuevo, Integer versionBase) {}
}
