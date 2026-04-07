package io.copaw.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Shared Jackson ObjectMapper factory for CoPaw persisted JSON.
 *
 * Python config/job files use snake_case, so Java must serialize and
 * deserialize with the same naming strategy to stay wire-compatible.
 */
public final class CoPawObjectMapperFactory {

    private CoPawObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
