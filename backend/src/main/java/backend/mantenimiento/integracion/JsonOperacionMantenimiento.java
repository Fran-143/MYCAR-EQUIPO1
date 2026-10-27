package backend.mantenimiento.integracion;

import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRepuesto;
import backend.mantenimiento.model.ResultadoRegistroMantenimiento;
import backend.mantenimiento.model.SolicitudRegistroMantenimiento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

/** Formato RF17 v1 estable: no depende de la configuración HTTP de Jackson. */
public final class JsonOperacionMantenimiento {
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonOperacionMantenimiento() { }

    public static String huella(SolicitudRegistroMantenimiento s) {
        var d = s.datos();
        var normalizados = new DatosRegistroMantenimiento(d.codigoTipoMantenimiento(),
                d.fechaServicio(), d.kmServicio(), d.origenKmServicio(),
                d.idLecturaKmReferencia(), numero(d.costoTotal()), d.servicioOtro(),
                d.proximaFechaSugerida(), d.proximoKmSugerido());
        List<DatosRepuesto> repuestos = s.repuestos() == null ? List.of()
                : s.repuestos().stream().map(p -> p == null ? null : new DatosRepuesto(
                        p.nombre(), p.marca(), p.cantidad(), numero(p.precioUnitario()))).toList();
        var contenido = new LinkedHashMap<String, Object>();
        contenido.put("contrato", "RF17_REGISTRAR_MANTENIMIENTO_V1");
        contenido.put("idVehiculo", s.idVehiculo());
        contenido.put("datos", normalizados);
        contenido.put("observaciones", s.observaciones());
        contenido.put("repuestos", repuestos);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(escribir(contenido).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private static BigDecimal numero(BigDecimal n) {
        return n == null ? null : n.stripTrailingZeros();
    }

    public static String escribir(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar la operación RF17", ex);
        }
    }

    public static ResultadoRegistroMantenimiento leerResultado(String json) {
        try {
            return JSON.readValue(json, ResultadoRegistroMantenimiento.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new IllegalStateException("El resultado confirmado RF17 es inválido", ex);
        }
    }
}
