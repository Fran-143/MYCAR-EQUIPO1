package backend.vehiculos.service;

import backend.vehiculos.dto.DatosCorregirKilometraje;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class HuellaCorregirKilometraje {
    private static final ObjectMapper CANONICO = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    public String calcular(long idVehiculo, DatosCorregirKilometraje datos) {
        try {
            var payload = new HuellaPayload(
                    idVehiculo,
                    datos.idLecturaBase(),
                    datos.kmCorregido(),
                    datos.motivo() == null ? null : datos.motivo().trim(),
                    datos.versionBase());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("MYCAR:RF16:v1\n".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(CANONICO.writeValueAsBytes(payload)));
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo calcular la huella RF16", ex);
        }
    }

    private record HuellaPayload(
            long idVehiculo,
            Long idLecturaBase,
            Integer kmCorregido,
            String motivo,
            Integer versionBase
    ) {}
}
