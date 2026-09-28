package com.ktb.moyeota.global.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;

@JacksonComponent
public class OffsetAwareLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    private final ZoneId zone;

    public OffsetAwareLocalDateTimeDeserializer() {
        this(ZoneId.systemDefault());
    }

    OffsetAwareLocalDateTimeDeserializer(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            TemporalAccessor parsed = parseWithOffset(parser.getString().trim());
            if (parsed != null) {
                return OffsetDateTime.from(parsed).atZoneSameInstant(zone).toLocalDateTime();
            }
        }
        return LocalDateTimeDeserializer.INSTANCE.deserialize(parser, context);
    }

    private static TemporalAccessor parseWithOffset(String text) {
        try {
            TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME.parse(text);
            return parsed.isSupported(ChronoField.OFFSET_SECONDS) ? parsed : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
