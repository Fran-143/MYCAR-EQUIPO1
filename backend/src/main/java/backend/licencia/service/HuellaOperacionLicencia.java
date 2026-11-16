package backend.licencia.service;

import backend.licencia.api.dto.ContratoLicencia;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;

public final class HuellaOperacionLicencia {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private HuellaOperacionLicencia() { }

    public static String registrar(long idUsuario, ContratoLicencia.RegistrarRequest req) {
        return hash("REGISTRAR_LICENCIA_V1", idUsuario, req);
    }

    public static String renovar(long idUsuario, ContratoLicencia.RenovarRequest req) {
        return hash("RENOVAR_LICENCIA_V1", idUsuario, req);
    }

    private static String hash(String contrato, long idUsuario, Object cuerpo) {
        var mapa = new LinkedHashMap<String, Object>();
        mapa.put("contrato", contrato);
        mapa.put("idUsuario", idUsuario);
        mapa.put("solicitud", cuerpo);
        try {
            String canon = JSON.writeValueAsString(mapa);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canon.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar operación licencia", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo calcular huella licencia", ex);
        }
    }
}