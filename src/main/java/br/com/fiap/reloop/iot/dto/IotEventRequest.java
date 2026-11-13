package br.com.fiap.reloop.iot.dto;

import java.io.IOException;
import java.math.BigDecimal;

import br.com.fiap.reloop.iot.model.IotEventType;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record IotEventRequest(
        @JsonDeserialize(using = TextDeserializer.class) @NotBlank @Size(max = 128) String eventId,
        @JsonDeserialize(using = TextDeserializer.class) @NotBlank @Size(max = 64) String deviceId,
        @JsonDeserialize(using = DisposalIdDeserializer.class) @NotNull @Positive Long disposalId,
        @NotNull IotEventType eventType,
        @JsonDeserialize(using = WeightDeserializer.class) @Positive BigDecimal weightKg) {

    @AssertTrue
    public boolean isWeightValidForEvent() {
        return (eventType == IotEventType.WEIGHT_RECORDED) == (weightKg != null);
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Campo não previsto no evento IoT: " + name);
    }

    static class TextDeserializer extends JsonDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(String.class, "O identificador deve ser texto.");
            }
            return parser.getText();
        }
    }

    static class DisposalIdDeserializer extends JsonDeserializer<Long> {

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Long.class, "disposalId deve ser um número inteiro.");
            }
            return parser.getLongValue();
        }
    }

    static class WeightDeserializer extends JsonDeserializer<BigDecimal> {

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.currentToken().isNumeric()) {
                return context.reportInputMismatch(BigDecimal.class, "weightKg deve ser um número.");
            }
            return parser.getDecimalValue();
        }

        @Override
        public BigDecimal getNullValue(DeserializationContext context) throws JsonMappingException {
            return context.reportInputMismatch(BigDecimal.class, "weightKg não aceita null explícito.");
        }

        @Override
        public BigDecimal getAbsentValue(DeserializationContext context) {
            return null;
        }
    }
}
