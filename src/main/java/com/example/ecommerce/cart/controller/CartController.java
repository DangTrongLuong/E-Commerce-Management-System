package com.example.ecommerce.cart.controller;

import com.example.ecommerce.cart.dto.AddToCartRequest;
import com.example.ecommerce.cart.dto.CartResponse;
import com.example.ecommerce.cart.dto.UpdateCartItemRequest;
import com.example.ecommerce.cart.service.CartService;
import com.example.ecommerce.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Giỏ Hàng (Cart)", description = "Quản lý giỏ hàng mua sắm của khách hàng cá nhân (Quyền: USER)")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(
            summary = "Lấy thông tin giỏ hàng hiện tại",
            description = "Truy xuất toàn bộ danh sách sản phẩm, số lượng, đơn giá và tổng thành tiền hiện có trong giỏ hàng của người dùng. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        CartResponse cart = cartService.getCart();
        return ResponseEntity.ok(ApiResponse.success("Lấy giỏ hàng thành công", cart));
    }

    @PostMapping("/items")
    @Operation(
            summary = "Thêm sản phẩm vào giỏ hàng",
            description = "Bổ sung một sản phẩm với số lượng yêu cầu vào giỏ hàng. Tự động kiểm tra số lượng tồn kho khả dụng và cộng dồn số lượng nếu sản phẩm đã có sẵn trong giỏ. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(@Valid @RequestBody AddToCartRequest request) {
        CartResponse cart = cartService.addToCart(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm sản phẩm vào giỏ hàng thành công", cart));
    }

    @PutMapping("/items/{productId}")
    @Operation(
            summary = "Cập nhật số lượng sản phẩm trong giỏ hàng",
            description = "Điều chỉnh số lượng của một sản phẩm trong giỏ hàng. Nếu số lượng cập nhật nhỏ hơn hoặc bằng 0, mặt hàng sẽ tự động bị xóa khỏi giỏ. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @PathVariable Integer productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartResponse cart = cartService.updateItemQuantity(productId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật giỏ hàng thành công", cart));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(
            summary = "Xóa một sản phẩm khỏi giỏ hàng",
            description = "Loại bỏ hoàn toàn một mặt hàng cụ thể ra khỏi giỏ hàng mua sắm của người dùng theo productId. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@PathVariable Integer productId) {
        CartResponse cart = cartService.removeItem(productId);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm khỏi giỏ hàng thành công", cart));
    }

    @DeleteMapping
    @Operation(
            summary = "Xóa toàn bộ sản phẩm trong giỏ hàng",
            description = "Xóa rỗng toàn bộ danh sách mặt hàng có trong giỏ hàng của người dùng hiện tại. Quyền truy cập: USER."
    )
    public ResponseEntity<ApiResponse<CartResponse>> clearCart() {
        CartResponse cart = cartService.clearCart();
        return ResponseEntity.ok(ApiResponse.success("Xóa sạch giỏ hàng thành công", cart));
    }
}
