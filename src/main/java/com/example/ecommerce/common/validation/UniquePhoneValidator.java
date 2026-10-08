package com.example.ecommerce.common.validation;

import com.example.ecommerce.customer.repository.CustomerRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class UniquePhoneValidator implements ConstraintValidator<UniquePhone, String> {

    @Autowired
    private CustomerRepository customerRepository;

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null || phone.trim().isEmpty()) {
            return true;
        }
        return !customerRepository.existsByPhone(phone.trim());
    }
}
