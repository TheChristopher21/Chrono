package com.chrono.chrono.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class UserPagePermissionsConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        try {
            Map<String, String> safeAttribute = attribute != null ? new LinkedHashMap<>(attribute) : new LinkedHashMap<>();
            return OBJECT_MAPPER.writeValueAsString(safeAttribute);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not serialize user page permissions.", exception);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }

        try {
            Map<String, String> value = OBJECT_MAPPER.readValue(dbData, MAP_TYPE);
            return value != null ? new LinkedHashMap<>(value) : new LinkedHashMap<>();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not deserialize user page permissions.", exception);
        }
    }
}
