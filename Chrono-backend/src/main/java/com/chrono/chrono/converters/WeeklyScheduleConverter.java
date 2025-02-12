package com.chrono.chrono.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Converter
public class WeeklyScheduleConverter implements AttributeConverter<List<Map<String, Integer>>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Map<String, Integer>> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            // Wandelt die Liste in einen JSON-String um
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting weeklySchedule to JSON string", e);
        }
    }

    @Override
    public List<Map<String, Integer>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            // Hier wird dbData (ein String) in ein List<Map<String, Integer>> umgewandelt.
            return objectMapper.readValue(dbData, new TypeReference<List<Map<String, Integer>>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSON string to weeklySchedule", e);
        }
    }
}
