package com.example.ecommerce.exception;

public class InvalidVnPaySignatureException extends RuntimeException {
  public InvalidVnPaySignatureException(String message) {
    super(message);
  }
}
