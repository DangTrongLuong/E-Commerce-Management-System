package com.example.ecommerce.common.config;

import com.example.ecommerce.product.dto.ProductImportRequest;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


@Configuration
@RequiredArgsConstructor
@Slf4j
public class ProductBatchConfig {

    private final ProductRepository productRepository;

    @Bean
    @StepScope
    public ItemReader<ProductImportRequest> productCsvItemReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new ItemReader<>() {
            private Iterator<CSVRecord> iterator;
            private CSVParser csvParser;

            private void init() {
                if (iterator == null && filePath != null) {
                    try {
                        File file = new File(filePath);
                        Reader reader = new FileReader(file, StandardCharsets.UTF_8);
                        CSVFormat format = CSVFormat.DEFAULT.builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .setIgnoreSurroundingSpaces(true)
                                .setIgnoreEmptyLines(true)
                                .build();
                        csvParser = new CSVParser(reader, format);
                        iterator = csvParser.iterator();
                    } catch (Exception e) {
                        log.error("Lỗi khi mở file CSV cho Spring Batch import: {}", e.getMessage());
                        throw new RuntimeException("Không thể đọc file CSV: " + e.getMessage(), e);
                    }
                }
            }

            @Override
            public ProductImportRequest read() {
                init();
                if (iterator != null && iterator.hasNext()) {
                    CSVRecord record = iterator.next();
                    String name = record.isMapped("name") ? record.get("name") : (record.size() > 0 ? record.get(0) : null);
                    String priceStr = record.isMapped("price") ? record.get("price") : (record.size() > 1 ? record.get(1) : null);
                    String stockStr = record.isMapped("stock") ? record.get("stock") : (record.size() > 2 ? record.get(2) : null);
                    String statusStr = record.isMapped("status") ? record.get("status") : (record.size() > 3 ? record.get(3) : null);

                    BigDecimal price = null;
                    try {
                        if (priceStr != null && !priceStr.isBlank()) {
                            price = new BigDecimal(priceStr.trim());
                        }
                    } catch (Exception ignored) {}

                    Integer stock = null;
                    try {
                        if (stockStr != null && !stockStr.isBlank()) {
                            stock = Integer.parseInt(stockStr.trim());
                        }
                    } catch (Exception ignored) {}

                    return ProductImportRequest.builder()
                            .name(name != null ? name.trim() : null)
                            .price(price)
                            .stock(stock)
                            .status(statusStr != null ? statusStr.trim() : null)
                            .build();
                }
                return null;
            }
        };
    }

    @Bean
    public ItemProcessor<ProductImportRequest, Product> productItemProcessor() {
        return req -> {
            if (req.getName() == null || req.getName().isBlank()) {
                log.warn("Record bị bỏ qua: Tên sản phẩm rỗng");
                return null;
            }

            if (req.getPrice() == null || req.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Record bị bỏ qua: Giá sản phẩm không hợp lệ ({})", req.getPrice());
                return null;
            }

            if (req.getStock() == null || req.getStock() < 0) {
                log.warn("Record bị bỏ qua: Tồn kho sản phẩm không hợp lệ ({})", req.getStock());
                return null;
            }

            if (productRepository.findByNameIgnoreCase(req.getName()).isPresent()) {
                log.warn("Record bị bỏ qua: Sản phẩm với tên '{}' đã tồn tại", req.getName());
                return null;
            }

            ProductStatus status = ProductStatus.ACTIVE;
            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                try {
                    status = ProductStatus.valueOf(req.getStatus().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Trạng thái '{}' không hợp lệ, gán mặc định ACTIVE", req.getStatus());
                }
            }

            return Product.builder()
                    .name(req.getName())
                    .price(req.getPrice())
                    .stock(req.getStock())
                    .status(status)
                    .build();
        };
    }


    @Bean
    public ItemWriter<Product> productItemWriter() {
        return chunk -> productRepository.saveAll(chunk.getItems());
    }

    @Bean
    public Step importProductStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<ProductImportRequest> productCsvItemReader,
            ItemProcessor<ProductImportRequest, Product> productItemProcessor,
            ItemWriter<Product> productItemWriter) {
        return new StepBuilder("importProductStep", jobRepository)
                .<ProductImportRequest, Product>chunk(10, transactionManager)
                .reader(productCsvItemReader)
                .processor(productItemProcessor)
                .writer(productItemWriter)
                .build();
    }


    @Bean
    public Job importProductJob(JobRepository jobRepository, Step importProductStep) {
        return new JobBuilder("importProductJob", jobRepository)
                .start(importProductStep)
                .build();
    }
}
