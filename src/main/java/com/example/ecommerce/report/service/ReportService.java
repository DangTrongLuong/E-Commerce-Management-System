package com.example.ecommerce.report.service;

import com.example.ecommerce.report.dto.request.ReportExportRequest;
import com.example.ecommerce.report.dto.response.ReportDefinitionResponse;

public interface ReportService {
    ReportDefinitionResponse getReportDefinition(String code);

    byte[] exportReport(String code, ReportExportRequest request);

    String getExportFileName(String reportCode, ReportExportRequest request);
}
