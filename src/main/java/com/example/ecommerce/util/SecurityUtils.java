package com.example.ecommerce.util;

import com.example.ecommerce.entity.AppUser;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final AppUserRepository userRepository;

    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            throw new AccessDeniedException("Unauthenticated caller");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    public boolean hasRole(Role role) {
        AppUser user = getCurrentUser();
        return user.getRole() == role;
    }

    public void verifyUserOrAdmin(Long customerId) {
        AppUser user = getCurrentUser();
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.USER && user.getCustomer() != null && user.getCustomer().getId() == customerId.intValue()) {
            return;
        }
        throw new AccessDeniedException("Forbidden: Caller does not own customer resource");
    }
}
