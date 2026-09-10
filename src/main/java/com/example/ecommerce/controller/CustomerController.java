package com.example.ecommerce.controller;

import com.example.ecommerce.dto.request.CustomerCreationRequest;
import com.example.ecommerce.dto.response.ApiResponse;
import com.example.ecommerce.dto.response.CustomerResponse;
import com.example.ecommerce.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer catalog endpoints")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerController {
    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    CustomerService customerService;

    @PostMapping
    @Operation(summary = "Tạo 1 khách hàng mới")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CustomerCreationRequest customerCreationRequest){

        CustomerResponse customerResponse = customerService.createCustomer(customerCreationRequest);
        log.info("Tạo khách hàng thành công: {}", customerResponse.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo khách hàng thành công !", customerResponse));

    }

    @GetMapping
    @Operation(summary = "Lấy thông tin danh sách khách hàng")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAllCustomer(){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy danh sách khách hàng thành công !", customerService.getCustomer()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết 1 khách hàng !")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable Integer id){
        CustomerResponse customerResponse = customerService.getCustomerById(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Lấy thành công khách hàng!", customerResponse));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Chỉnh sửa thông tin khách hàng")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable Integer id,
            @Valid @RequestBody CustomerCreationRequest customerCreationRequest
            ){

        CustomerResponse customerResponse = customerService.updateCustomer(id, customerCreationRequest);

        log.info("Cập nhật thành công khách hàng: {}", id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Cập nhật khách hàng thành công !", customerResponse));

    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa khách hàng")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Integer id){
        customerService.deteleCustomer(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                        "Xóa khách hàng thành công !",
                        null
                ));
    }

}
