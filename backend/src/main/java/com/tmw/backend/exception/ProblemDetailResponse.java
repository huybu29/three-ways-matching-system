package com.tmw.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String code;
}