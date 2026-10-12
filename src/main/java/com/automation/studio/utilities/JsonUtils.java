package com.automation.studio.utilities;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared JSON helpers (Jackson). */
public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonUtils() {}

    public static ObjectMapper mapper() { return MAPPER; }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        return MAPPER.readValue(Files.readString(file), type);
    }

    public static void write(Path file, Object value) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, MAPPER.writeValueAsString(value));
    }
}
