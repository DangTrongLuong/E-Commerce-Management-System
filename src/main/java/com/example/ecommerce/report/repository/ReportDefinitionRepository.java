package com.example.ecommerce.report.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.ecommerce.report.entity.ReportDefinition;

@Repository
public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, String> {
    @EntityGraph(attributePaths = { "params" })
    Optional<ReportDefinition> findByCode(String code);
}
