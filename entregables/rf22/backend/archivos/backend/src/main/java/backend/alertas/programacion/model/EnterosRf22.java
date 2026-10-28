package backend.alertas.programacion.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

/** No truncar decimales ni aceptar cadenas como identificadores/intervalos. Configuración local de RF22. */
public final class EnterosRf22 {
    private EnterosRf22() { }
    public static class Entero extends JsonDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser p, DeserializationContext c) throws IOException {
            JsonNode n=p.readValueAsTree();
            if (!n.isIntegralNumber() || !n.canConvertToInt()) throw c.weirdStringException("", Integer.class, "Se requiere un entero representable");
            return n.intValue();
        }
    }
    public static class Largo extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser p, DeserializationContext c) throws IOException {
            JsonNode n=p.readValueAsTree();
            if (!n.isIntegralNumber() || !n.canConvertToLong()) throw c.weirdStringException("", Long.class, "Se requiere un identificador entero");
            return n.longValue();
        }
    }
}
