package backend.vehiculos.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Coerción estricta solo para los campos numéricos del registro RF08 / RF14. */
public final class NumerosEnterosRegistroJson {
    public static final BigDecimal KM_MAXIMO = new BigDecimal("9999999.99");

    private NumerosEnterosRegistroJson() { }

    public static final class Entero extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) contexto.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public static final class Identificador extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Long) contexto.handleUnexpectedToken(Long.class, parser);
            }
            return parser.getLongValue();
        }
    }

    /** Solo número (entero o hasta 2 decimales). Normaliza a escala 2. */
    public static final class Kilometraje extends JsonDeserializer<BigDecimal> {
        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT) && !parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return (BigDecimal) contexto.handleUnexpectedToken(BigDecimal.class, parser);
            }
            BigDecimal valor = parser.getDecimalValue();
            if (valor.scale() > 2) {
                BigDecimal sinCeros = valor.stripTrailingZeros();
                if (sinCeros.scale() > 2) {
                    return (BigDecimal) contexto.handleUnexpectedToken(BigDecimal.class, parser);
                }
            }
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        }
    }

    public static boolean esKilometrajeValido(BigDecimal km) {
        return km != null
                && km.compareTo(BigDecimal.ZERO) > 0
                && km.compareTo(KM_MAXIMO) <= 0
                && km.scale() <= 2;
    }
}
