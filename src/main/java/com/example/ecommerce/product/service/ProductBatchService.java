package com.example.ecommerce.product.service;

import com.example.ecommerce.common.util.SecurityUtils;
import com.example.ecommerce.product.dto.ProductImportResultResponse;
import com.example.ecommerce.user.entity.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBatchService {

    private final JobLauncher jobLauncher;
    private final Job importProductJob;
    private final SecurityUtils securityUtils;

    public ProductImportResultResponse importProductsFromCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File CSV tải lên không được rỗng!");
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("product_import_", ".csv");
            file.transferTo(tempFile);

            AppUser currentUser = null;
            try {
                currentUser = securityUtils.getCurrentUser();
            } catch (Exception e) {
                log.warn("Không thể lấy thông tin người dùng từ SecurityContext khi import sản phẩm: {}", e.getMessage());
            }

            Long ownerId = (currentUser != null) ? currentUser.getId() : null;

            JobParametersBuilder parametersBuilder = new JobParametersBuilder()
                    .addString("filePath", tempFile.getAbsolutePath())
                    .addLong("timestamp", System.currentTimeMillis());

            if (ownerId != null) {
                parametersBuilder.addLong("ownerId", ownerId);
            }

            JobParameters jobParameters = parametersBuilder.toJobParameters();

            JobExecution jobExecution = jobLauncher.run(importProductJob, jobParameters);

            long total = 0;
            long success = 0;
            long failed = 0;

            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                total += stepExecution.getReadCount();
                success += stepExecution.getWriteCount();
                failed += (stepExecution.getReadCount() - stepExecution.getWriteCount());
            }

            log.info("Batch job import kết thúc với trạng thái: {}. Total: {}, Success: {}, Failed: {}, Owner ID: {}",
                    jobExecution.getStatus(), total, success, failed, ownerId);

            return ProductImportResultResponse.builder()
                    .total(total)
                    .success(success)
                    .failed(failed)
                    .build();

        } catch (Exception e) {
            log.error("Xảy ra lỗi khi chạy batch import sản phẩm: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi khi import file CSV: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    tempFile.deleteOnExit();
                }
            }
        }
    }
}

