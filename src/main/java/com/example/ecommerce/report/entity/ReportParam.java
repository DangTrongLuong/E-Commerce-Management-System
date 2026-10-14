package com.example.ecommerce.report.entity;

import com.example.ecommerce.report.enums.ReportParamDataType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "report_param")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_code", nullable = false)
    private String reportCode;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private ReportParamDataType dataType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "param_order", nullable = false)
    private int paramOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_code", insertable = false, updatable = false)
    private ReportDefinition reportDefinition;
}
