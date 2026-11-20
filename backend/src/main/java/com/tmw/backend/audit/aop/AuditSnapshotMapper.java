package com.tmw.backend.audit.aop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuditSnapshotMapper {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper safeMapper;

    public AuditSnapshotMapper() {
        this.safeMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    public Map<String, Object> toSnapshotMap(Object target) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> rawMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
   
        return safeMapper.convertValue(target, MAP_TYPE);
    }
}