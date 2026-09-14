package com.example.ecommerce.service;

import com.example.ecommerce.dto.response.ProductImportResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductBatchService {

    private final JobLauncher jobLauncher;
    private final Job importProductJob;

    public ProductImportResultResponse importProductsFromCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File CSV tải lên không được rỗng!");
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("product_import_", ".csv");
            file.transferTo(tempFile);

            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("filePath", tempFile.getAbsolutePath())
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(importProductJob, jobParameters);

            long total = 0;
            long success = 0;
            long failed = 0;

            for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                total += stepExecution.getReadCount();
                success += stepExecution.getWriteCount();
                failed += (stepExecution.getReadCount() - stepExecution.getWriteCount());
            }

            log.info("Batch job import kết thúc với trạng thái: {}. Total: {}, Success: {}, Failed: {}",
                    jobExecution.getStatus(), total, success, failed);

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
