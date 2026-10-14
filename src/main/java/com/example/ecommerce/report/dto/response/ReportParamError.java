package com.example.ecommerce.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportParamError {
    private String param;
    private String reason;
    private String expected;

    public ReportParamError(String param, String reason) {
        this.param = param;
        this.reason = reason;
    }

}
