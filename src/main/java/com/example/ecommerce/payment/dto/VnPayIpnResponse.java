package com.example.ecommerce.payment.dto;

public record VnPayIpnResponse(String RspCode, String Message) {

    public static VnPayIpnResponse of(String code, String message) {
        return new VnPayIpnResponse(code, message);
    }

    public static VnPayIpnResponse success() {
        return of("00", "Xử lý giao dịch thành công.");
    }

    public static VnPayIpnResponse orderNotFound() {
        return of("01", "Không tìm thấy đơn hàng trong hệ thống.");
    }

    public static VnPayIpnResponse alreadyConfirmed() {
        return of("02", "Đơn hàng đã được cập nhật trước đó (tránh xử lý trùng).");
    }

    public static VnPayIpnResponse invalidAmount() {
        return of("04", "Số tiền thanh toán không khớp với đơn hàng gốc.");
    }

    public static VnPayIpnResponse invalidSignature() {
        return of("97", "Sai chữ ký checksum (dữ liệu có thể bị can thiệp).");
    }

    public static VnPayIpnResponse unknownError() {
        return of("99", "Lỗi hệ thống chưa xác định.");
    }
}
