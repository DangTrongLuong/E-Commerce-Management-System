package com.example.ecommerce.common.exception;

import java.util.List;

import com.example.ecommerce.report.dto.response.ReportParamError;

import lombok.Getter;

@Getter
public class InvalidReportParamsException extends RuntimeException {
    private List<ReportParamError> errors;

    public InvalidReportParamsException(String message, List<ReportParamError> errors) {
        super(message);
        this.errors = errors;
    }
}
