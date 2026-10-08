package com.example.ecommerce.common.util;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.user.repository.AppUserRepository;
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
            throw new AccessDeniedException("Yêu cầu xác thực tài khoản đăng nhập");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
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
        throw new AccessDeniedException("Bạn không có quyền truy cập thông tin của khách hàng này");
    }
}
