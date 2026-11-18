package backend.tive.service;

import backend.tive.api.dto.ContratoTive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;

public final class HuellaOperacionTive {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private HuellaOperacionTive() { }

    public static String registrar(long idVehiculo, ContratoTive.RegistrarRequest req) {
        return hash("REGISTRAR_TIVE_V1", idVehiculo, req);
    }

    private static String hash(String contrato, long idVehiculo, Object cuerpo) {
        var mapa = new LinkedHashMap<String, Object>();
        mapa.put("contrato", contrato);
        mapa.put("idVehiculo", idVehiculo);
        mapa.put("solicitud", cuerpo);
        try {
            String canon = JSON.writeValueAsString(mapa);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar operación TIVE", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular huella TIVE", ex);
        }
    }
}
