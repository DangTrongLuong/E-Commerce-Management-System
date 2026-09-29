package com.example.ecommerce.common.exception;

public class InvalidVnPaySignatureException extends RuntimeException {
  public InvalidVnPaySignatureException(String message) {
    super(message);
  }
}
