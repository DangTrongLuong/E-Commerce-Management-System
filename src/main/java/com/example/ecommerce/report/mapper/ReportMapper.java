package com.example.ecommerce.report.mapper;

import com.example.ecommerce.report.dto.response.ReportDefinitionResponse;
import com.example.ecommerce.report.dto.response.ReportParamResponse;
import com.example.ecommerce.report.entity.ReportDefinition;
import com.example.ecommerce.report.entity.ReportParam;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportMapper {
    ReportDefinitionResponse toDefinitionResponse(ReportDefinition reportDefinition);

    ReportParamResponse toParamResponse(ReportParam reportParam);
}
