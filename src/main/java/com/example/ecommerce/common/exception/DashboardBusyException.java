package com.example.ecommerce.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class DashboardBusyException extends RuntimeException {
    public DashboardBusyException(String message) {
        super(message);
    }

    public DashboardBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
