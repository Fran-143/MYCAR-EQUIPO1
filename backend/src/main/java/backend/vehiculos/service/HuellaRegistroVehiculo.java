package backend.vehiculos.service;

import backend.vehiculos.dto.DatosRegistroVehiculo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huella calculada por el servidor. No usa normalización silenciosa. */
@Component
public class HuellaRegistroVehiculo {
    private static final ObjectMapper CANONICO = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    public String calcular(DatosRegistroVehiculo datos) {
        try {
            // El formato v1 y todos los campos (incluidos null) se mantienen estables.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("MYCAR:RF08:v1\n".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(CANONICO.writeValueAsBytes(datos)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("No se pudo calcular la huella del registro", ex);
        }
    }
}
