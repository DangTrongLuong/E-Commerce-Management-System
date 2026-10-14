package com.example.ecommerce.report.dto.request;

import java.util.Map;

import lombok.Data;

@Data
public class ReportExportRequest {
    private String format;
    private Map<String, Object> params;
}
