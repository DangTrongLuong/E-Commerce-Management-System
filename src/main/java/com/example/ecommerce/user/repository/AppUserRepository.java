package com.example.ecommerce.user.repository;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndStatus(Role role, UserStatus status);

    Optional<AppUser> findByCustomerId(Long customerId);
}
