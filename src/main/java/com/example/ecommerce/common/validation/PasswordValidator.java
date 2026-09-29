package com.example.ecommerce.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_CHAR = Pattern.compile(".*[!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~].*");

    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        // no-op
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            // Để @NotBlank xử lý trường hợp rỗng, ở đây coi như hợp lệ để tránh trùng lỗi
            return true;
        }

        boolean isValid = true;
        context.disableDefaultConstraintViolation();

        if (password.length() < MIN_LENGTH) {
            addViolation(context, "Mật khẩu phải có ít nhất " + MIN_LENGTH + " ký tự");
            isValid = false;
        }

        if (!UPPERCASE.matcher(password).matches()) {
            addViolation(context, "Mật khẩu phải chứa ít nhất 1 chữ hoa");
            isValid = false;
        }

        if (!LOWERCASE.matcher(password).matches()) {
            addViolation(context, "Mật khẩu phải chứa ít nhất 1 chữ thường");
            isValid = false;
        }

        if (!DIGIT.matcher(password).matches()) {
            addViolation(context, "Mật khẩu phải chứa ít nhất 1 chữ số");
            isValid = false;
        }

        if (!SPECIAL_CHAR.matcher(password).matches()) {
            addViolation(context, "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt (!@#$%^&*...)");
            isValid = false;
        }

        return isValid;
    }

    private void addViolation(ConstraintValidatorContext context, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
