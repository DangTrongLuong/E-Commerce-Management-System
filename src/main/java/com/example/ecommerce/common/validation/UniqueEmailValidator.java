package com.example.ecommerce.common.validation;

import com.example.ecommerce.user.repository.AppUserRepository;
import com.example.ecommerce.customer.repository.CustomerRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.regex.Pattern;

public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AppUserRepository userRepository;

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isEmpty()) return true;

        boolean isCorrectFormat = Pattern.matches(EMAIL_REGEX, email);

        if (!isCorrectFormat) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Email format is invalid")
                    .addConstraintViolation();
            return false;
        }

        return !customerRepository.existsByEmail(email) && !userRepository.existsByEmail(email);
    }
}
