package com.example.ecommerce.dashboard.service;

import com.example.ecommerce.dashboard.config.DashboardDemoProperties;
import com.example.ecommerce.dashboard.config.DashboardProperties;
import com.example.ecommerce.dashboard.dto.DashboardResponse;
import com.example.ecommerce.dashboard.dto.LowStockProductResponse;
import com.example.ecommerce.dashboard.dto.TopProductResponse;
import com.example.ecommerce.order.repository.OrderRepository;
import com.example.ecommerce.order.repository.TopProductProjection;
import com.example.ecommerce.product.repository.LowStockProductProjection;
import com.example.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Qualifier("dashboardExecutor")
    private final ThreadPoolTaskExecutor dashboardExecutor;

    private final DashboardProperties dashboardProperties;
    private final DashboardDemoProperties demoProperties;

    private static class PartResult<T> {
        final String partName;
        final T data;
        final boolean success;

        PartResult(String partName, T data, boolean success) {
            this.partName = partName;
            this.data = data;
            this.success = success;
        }
    }

    @Override
    public DashboardResponse getDashboard(LocalDate date, String mode) {
        if (date == null) {
            date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting dashboard aggregation for date: {}, mode: {}", date, mode);

        if ("sequential".equalsIgnoreCase(mode)) {
            return getSequentialDashboard(date, startTime);
        }

        return getParallelDashboard(date, startTime);
    }

    private DashboardResponse getParallelDashboard(LocalDate date, long startTime) {
        long timeoutMs = dashboardProperties.getPartTimeout().toMillis();

        CompletableFuture<PartResult<BigDecimal>> revenueFuture = CompletableFuture
                .supplyAsync(() -> executePart("revenueToday", () -> fetchRevenueToday(date)), dashboardExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((res, ex) -> handleCompletion("revenueToday", res, ex, timeoutMs));

        CompletableFuture<PartResult<Map<String, Long>>> orderCountFuture = CompletableFuture
                .supplyAsync(() -> executePart("orderCountByStatus", this::fetchOrderCountByStatus), dashboardExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((res, ex) -> handleCompletion("orderCountByStatus", res, ex, timeoutMs));

        CompletableFuture<PartResult<List<TopProductResponse>>> topProductsFuture = CompletableFuture
                .supplyAsync(() -> executePart("top5Products", () -> fetchTop5Products(date)), dashboardExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((res, ex) -> handleCompletion("top5Products", res, ex, timeoutMs));

        CompletableFuture<PartResult<List<LowStockProductResponse>>> lowStockFuture = CompletableFuture
                .supplyAsync(() -> executePart("lowStockProducts", this::fetchLowStockProducts), dashboardExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((res, ex) -> handleCompletion("lowStockProducts", res, ex, timeoutMs));

        // Wait for all 4 futures to complete (or time out)
        CompletableFuture.allOf(revenueFuture, orderCountFuture, topProductsFuture, lowStockFuture).join();

        PartResult<BigDecimal> revenueRes = revenueFuture.join();
        PartResult<Map<String, Long>> orderCountRes = orderCountFuture.join();
        PartResult<List<TopProductResponse>> topProductsRes = topProductsFuture.join();
        PartResult<List<LowStockProductResponse>> lowStockRes = lowStockFuture.join();

        List<String> failedParts = new ArrayList<>();
        if (!revenueRes.success) failedParts.add(revenueRes.partName);
        if (!orderCountRes.success) failedParts.add(orderCountRes.partName);
        if (!topProductsRes.success) failedParts.add(topProductsRes.partName);
        if (!lowStockRes.success) failedParts.add(lowStockRes.partName);

        boolean partial = !failedParts.isEmpty();
        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("Dashboard aggregation completed in {} ms (partial: {}, failedParts: {})", elapsedMs, partial, failedParts);

        return DashboardResponse.builder()
                .date(date.toString())
                .revenueToday(revenueRes.data)
                .orderCountByStatus(orderCountRes.data)
                .top5Products(topProductsRes.data)
                .lowStockProducts(lowStockRes.data)
                .partial(partial)
                .failedParts(failedParts)
                .elapsedMs(elapsedMs)
                .build();
    }

    private DashboardResponse getSequentialDashboard(LocalDate date, long startTime) {
        List<String> failedParts = new ArrayList<>();

        BigDecimal revenueToday = null;
        try {
            revenueToday = executePart("revenueToday", () -> fetchRevenueToday(date));
        } catch (Exception ex) {
            log.error("Task [revenueToday] failed: {}", ex.getMessage(), ex);
            failedParts.add("revenueToday");
        }

        Map<String, Long> orderCountByStatus = null;
        try {
            orderCountByStatus = executePart("orderCountByStatus", this::fetchOrderCountByStatus);
        } catch (Exception ex) {
            log.error("Task [orderCountByStatus] failed: {}", ex.getMessage(), ex);
            failedParts.add("orderCountByStatus");
        }

        List<TopProductResponse> top5Products = null;
        try {
            top5Products = executePart("top5Products", () -> fetchTop5Products(date));
        } catch (Exception ex) {
            log.error("Task [top5Products] failed: {}", ex.getMessage(), ex);
            failedParts.add("top5Products");
        }

        List<LowStockProductResponse> lowStockProducts = null;
        try {
            lowStockProducts = executePart("lowStockProducts", this::fetchLowStockProducts);
        } catch (Exception ex) {
            log.error("Task [lowStockProducts] failed: {}", ex.getMessage(), ex);
            failedParts.add("lowStockProducts");
        }

        boolean partial = !failedParts.isEmpty();
        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("Dashboard (sequential) completed in {} ms (partial: {}, failedParts: {})", elapsedMs, partial, failedParts);

        return DashboardResponse.builder()
                .date(date.toString())
                .revenueToday(revenueToday)
                .orderCountByStatus(orderCountByStatus)
                .top5Products(top5Products)
                .lowStockProducts(lowStockProducts)
                .partial(partial)
                .failedParts(failedParts)
                .elapsedMs(elapsedMs)
                .build();
    }

    private <T> T executePart(String partName, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        applyDemoDelay(partName);
        applyDemoFail(partName);

        T result = supplier.get();
        long elapsed = System.currentTimeMillis() - start;
        log.info("Task [{}] completed in {} ms", partName, elapsed);
        return result;
    }

    private <T> PartResult<T> handleCompletion(String partName, T res, Throwable ex, long timeoutMs) {
        if (ex != null) {
            Throwable cause = (ex instanceof CompletionException || ex instanceof ExecutionException)
                    ? ex.getCause() : ex;
            if (cause instanceof TimeoutException) {
                log.warn("Task [{}] timed out after {} ms", partName, timeoutMs);
            } else {
                log.error("Task [{}] failed: {}", partName, cause != null ? cause.getMessage() : "Unknown error", cause);
            }
            return new PartResult<>(partName, null, false);
        }
        return new PartResult<>(partName, res, true);
    }

    private void applyDemoDelay(String partName) {
        if (demoProperties.getDelay() != null) {
            Duration delay = demoProperties.getDelay().get(partName);
            if (delay != null && delay.toMillis() > 0) {
                try {
                    log.info("Simulating delay of {} ms for task [{}]", delay.toMillis(), partName);
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Task [{}] sleep interrupted", partName);
                }
            }
        }
    }

    private void applyDemoFail(String partName) {
        if (demoProperties.getFail() != null && demoProperties.getFail().contains(partName)) {
            log.error("Simulated failure triggered for task [{}]", partName);
            throw new RuntimeException("Simulated error in task [" + partName + "]");
        }
    }

    private BigDecimal fetchRevenueToday(LocalDate date) {
        BigDecimal revenue = orderRepository.sumRevenueByDate(date);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    private Map<String, Long> fetchOrderCountByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", 0L);
        counts.put("AWAITING_PAYMENT", 0L);
        counts.put("PAID", 0L);
        counts.put("CONFIRMED", 0L);
        counts.put("COMPLETED", 0L);
        counts.put("CANCELLED", 0L);

        List<Object[]> rows = orderRepository.countOrdersByStatus();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] != null) {
                    String status = row[0].toString();
                    Long count = ((Number) row[1]).longValue();
                    counts.put(status, count);
                }
            }
        }
        return counts;
    }

    private List<TopProductResponse> fetchTop5Products(LocalDate date) {
        LocalDate startDate = date.minusDays(29);
        LocalDate endDate = date;
        List<TopProductProjection> list = orderRepository.findTop5Products(startDate, endDate);
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(p -> new TopProductResponse(p.getProductId(), p.getName(), p.getQuantitySold()))
                .toList();
    }

    private List<LowStockProductResponse> fetchLowStockProducts() {
        List<LowStockProductProjection> list = productRepository.findLowStockProducts();
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(p -> new LowStockProductResponse(p.getProductId(), p.getName(), p.getStock()))
                .toList();
    }
}
