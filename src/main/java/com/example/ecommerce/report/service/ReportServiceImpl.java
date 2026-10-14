package com.example.ecommerce.report.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.coyote.BadRequestException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.common.exception.BadRequestExeption;
import com.example.ecommerce.common.exception.InvalidReportParamsException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.customer.repository.CustomerRepository;
import com.example.ecommerce.report.dto.request.ReportExportRequest;
import com.example.ecommerce.report.dto.response.ReportDefinitionResponse;
import com.example.ecommerce.report.dto.response.ReportParamError;
import com.example.ecommerce.report.entity.ReportDefinition;
import com.example.ecommerce.report.entity.ReportParam;
import com.example.ecommerce.report.enums.ReportParamDataType;
import com.example.ecommerce.report.mapper.ReportMapper;
import com.example.ecommerce.report.repository.ReportDefinitionRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ReportServiceImpl implements ReportService {
    ReportDefinitionRepository reportDefinitionRepository;
    CustomerRepository customerRepository;
    DataSource dataSource;
    ReportMapper reportMapper;

    Map<String, JasperReport> reportCache = new ConcurrentHashMap<>();

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    @Transactional(readOnly = true)
    public ReportDefinitionResponse getReportDefinition(String code) {
        ReportDefinition reportDefinition = findReportByCodeOrThrow(code);

        return reportMapper.toDefinitionResponse(reportDefinition);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportReport(String code, ReportExportRequest reportExportRequest) {
        ReportDefinition reportDefinition = findReportByCodeOrThrow(code);

        String rawFormat = reportExportRequest.getFormat();
        String format = (rawFormat != null && !rawFormat.isBlank()) ? rawFormat.trim().toUpperCase() : "PDF";

        if (!"PDF".equals(format) && !"XLSX".equals(format)) {
            throw new BadRequestExeption(
                    "Định dạng xuất báo cáo không hợp lệ: '" + rawFormat + "'. Hệ thống chỉ hỗ trợ 'PDF' hoặc 'XLSX'.");
        }

        Map<String, Object> rawParams = reportExportRequest.getParams() != null
                ? reportExportRequest.getParams()
                : Collections.emptyMap();

        Map<String, Object> jasperParams = validateAndBuildParams(reportDefinition, rawParams);

        try {
            JasperReport jasperReport = getOrCompileReport(reportDefinition.getTemplatePath());

            try (Connection connection = dataSource.getConnection()) {
                JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, jasperParams, connection);

                if ("XLSX".equals(format)) {
                    return exportToXlsx(jasperPrint);
                } else {
                    return JasperExportManager.exportReportToPdf(jasperPrint);
                }
            }
        } catch (InvalidReportParamsException | BadRequestExeption | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi kết xuất báo cáo [{}]: {}", code, e.getMessage(), e);
            throw new BadRequestExeption("Không thể tạo file báo cáo: "
                    + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
    }

    @Override
    public String getExportFileName(String reportCode, ReportExportRequest reportExportRequest) {
        Map<String, Object> params = reportExportRequest.getParams() != null
                ? reportExportRequest.getParams()
                : Collections.emptyMap();

        String fromDateStr = String.valueOf(params.getOrDefault("fromDate", "")).replace("-", "");
        String toDateStr = String.valueOf(params.getOrDefault("toDate", "")).replace("-", "");

        if (fromDateStr.isBlank() || toDateStr.isBlank()) {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            fromDateStr = today;
            toDateStr = today;
        }

        String ext = "XLSX".equalsIgnoreCase(reportExportRequest.getFormat()) ? "xlsx" : "pdf";

        return String.format("%s_%s_%s.%s", reportCode, fromDateStr, toDateStr, ext);
    }

    // =========================================================================
    // HELPER METHOD
    // =========================================================================

    private Map<String, Object> validateAndBuildParams(ReportDefinition reportDef, Map<String, Object> rawParams) {
        List<ReportParamError> errors = new ArrayList<>();
        Map<String, Object> jasperParams = new HashMap<>();
        LocalDate fromDate = null;
        LocalDate toDate = null;
        Long customerId = null;
        for (ReportParam param : reportDef.getParams()) {
            String pName = param.getName();
            Object rawVal = rawParams.get(pName);
            // 1. Kiểm tra REQUIRED
            if (rawVal == null || rawVal.toString().trim().isEmpty()) {
                if (param.isRequired()) {
                    errors.add(new ReportParamError(pName, "REQUIRED"));
                }
                jasperParams.put(pName, null);
                continue;
            }
            String valStr = rawVal.toString().trim();
            // 2. Ép kiểu và kiểm tra định dạng theo DATA_TYPE
            if (param.getDataType() == ReportParamDataType.DATE) {
                if (!DATE_PATTERN.matcher(valStr).matches()) {
                    ReportParamError err = new ReportParamError(pName, "INVALID_FORMAT");
                    err.setExpected("yyyy-MM-dd");
                    errors.add(err);
                } else {
                    try {
                        LocalDate d = LocalDate.parse(valStr, DATE_FORMATTER);
                        jasperParams.put(pName, valStr);
                        if ("fromDate".equals(pName))
                            fromDate = d;
                        if ("toDate".equals(pName))
                            toDate = d;
                    } catch (Exception ex) {
                        ReportParamError err = new ReportParamError(pName, "INVALID_FORMAT");
                        err.setExpected("yyyy-MM-dd");
                        errors.add(err);
                    }
                }
            } else if (param.getDataType() == ReportParamDataType.LONG) {
                try {
                    long lVal = (rawVal instanceof Number) ? ((Number) rawVal).longValue() : Long.parseLong(valStr);
                    jasperParams.put(pName, lVal);
                    if ("customerId".equals(pName)) {
                        customerId = lVal;
                    }
                } catch (Exception ex) {
                    errors.add(new ReportParamError(pName, "INVALID_TYPE"));
                }
            } else if (param.getDataType() == ReportParamDataType.DECIMAL) {
                try {
                    BigDecimal dec = (rawVal instanceof BigDecimal) ? (BigDecimal) rawVal
                            : (rawVal instanceof Number) ? BigDecimal.valueOf(((Number) rawVal).doubleValue())
                                    : new BigDecimal(valStr);
                    jasperParams.put(pName, dec);
                } catch (Exception ex) {
                    errors.add(new ReportParamError(pName, "INVALID_TYPE"));
                }
            } else {
                jasperParams.put(pName, valStr);
            }
        }
        // 3. Kiểm tra ngày: RANGE_INVALID và RANGE_TOO_LONG
        if (fromDate != null && toDate != null) {
            if (toDate.isBefore(fromDate)) {
                errors.add(new ReportParamError("toDate", "RANGE_INVALID"));
            } else if (ChronoUnit.DAYS.between(fromDate, toDate) > 92) {
                errors.add(new ReportParamError("toDate", "RANGE_TOO_LONG"));
            }
        }
        // 4. Kiểm tra customerId: NOT_FOUND
        String customerDisplayName = "Tất cả";
        if (customerId != null) {
            var custOpt = customerRepository.findById(customerId.intValue());
            if (custOpt.isEmpty()) {
                errors.add(new ReportParamError("customerId", "NOT_FOUND"));
            } else {
                customerDisplayName = custOpt.get().getName();
            }
        }
        // BẮT BUỘC TRẢ MỌI LỖI TRONG 1 RESPONSE (Rule 358)
        if (!errors.isEmpty()) {
            throw new InvalidReportParamsException("Tham số báo cáo không hợp lệ", errors);
        }
        // Bổ sung các thông số hiển thị cho Header báo cáo
        jasperParams.put("customerName", customerDisplayName);
        if (fromDate != null && toDate != null) {
            jasperParams.put("dateRangeText", String.format("Từ ngày %s đến ngày %s",
                    fromDate.format(DISPLAY_DATE_FORMATTER), toDate.format(DISPLAY_DATE_FORMATTER)));
        } else {
            jasperParams.put("dateRangeText", "");
        }
        String exportTime = "Xuất lúc: " + LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(EXPORT_TIME_FORMATTER) + " (UTC+7)";
        jasperParams.put("exportTime", exportTime);
        return jasperParams;
    }

    private JasperReport getOrCompileReport(String templatePath) throws Exception {
        if (reportCache.containsKey(templatePath)) {
            return reportCache.get(templatePath);
        }
        ClassPathResource resource = new ClassPathResource(templatePath);
        if (!resource.exists()) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy file mẫu báo cáo (.jrxml) tại đường dẫn: " + templatePath);
        }
        try (InputStream is = resource.getInputStream()) {
            JasperReport compiled = JasperCompileManager.compileReport(is);
            reportCache.put(templatePath, compiled);
            return compiled;
        }
    }

    private byte[] exportToXlsx(JasperPrint jasperPrint) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JRXlsxExporter exporter = new JRXlsxExporter();
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));
        SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
        config.setDetectCellType(true); // Cột Doanh thu có kiểu số (Rule 368)
        config.setRemoveEmptySpaceBetweenRows(true);
        config.setWhitePageBackground(false);
        exporter.setConfiguration(config);
        exporter.exportReport();
        return baos.toByteArray();
    }

    private ReportDefinition findReportByCodeOrThrow(String code) {
        return reportDefinitionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo với mã: " + code));
    }

}