package com.example.ecommerce.controller;

import com.example.ecommerce.product.dto.ProductImportResultResponse;
import com.example.ecommerce.product.service.ProductBatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductBatchService productBatchService;

    @MockBean
    private com.example.ecommerce.notification.service.EmailService emailService;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/products/import - Import file CSV thành công HTTP 200")
    void importProducts_Success_Returns200() throws Exception {
        String csvContent = "name,price,stock,status\n" +
                "Phone A,500.00,10,ACTIVE\n" +
                "Phone B,-10,5,ACTIVE\n";

        MockMultipartFile csvFile = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                csvContent.getBytes()
        );

        ProductImportResultResponse result = ProductImportResultResponse.builder()
                .total(2)
                .success(1)
                .failed(1)
                .build();

        when(productBatchService.importProductsFromCsv(any())).thenReturn(result);

        mockMvc.perform(multipart("/api/products/import").file(csvFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed").value(1));
    }
}
