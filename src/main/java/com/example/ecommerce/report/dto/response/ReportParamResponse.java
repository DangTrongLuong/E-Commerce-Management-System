package com.example.ecommerce.report.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReportParamResponse {
    private String name;
    private String dataType;
    private boolean required;
    private int paramOrder;
}
