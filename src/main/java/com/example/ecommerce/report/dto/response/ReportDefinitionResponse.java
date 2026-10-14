package com.example.ecommerce.report.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportDefinitionResponse {
    private String code;
    private String name;
    private String description;
    private List<ReportParamResponse> params;
}
