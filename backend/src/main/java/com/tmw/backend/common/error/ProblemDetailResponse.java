package com.tmw.backend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse {

    private final String type;
    private final String title;
    private final int status;
    private final String code;
    private final String detail;
    private final String instance;

    @JsonProperty("trace_id")
    private final String traceId;

    private final List<ProblemFieldError> errors;

    private final Object capacity;
}