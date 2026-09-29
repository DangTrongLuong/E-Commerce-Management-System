package com.example.ecommerce.common.security;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.UserStatus;
import com.example.ecommerce.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        boolean enabled = user.getStatus() == UserStatus.ACTIVE;
        boolean accountNonLocked = user.getStatus() != UserStatus.LOCKED;
        if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            accountNonLocked = true;
        }

        String roleName = user.getRole().name().startsWith("ROLE_")
                ? user.getRole().name()
                : "ROLE_" + user.getRole().name();

        return new User(
                user.getEmail(),
                user.getPasswordHash(),
                enabled,
                true,
                true,
                accountNonLocked,
                List.of(new SimpleGrantedAuthority(roleName))
        );
    }
}
