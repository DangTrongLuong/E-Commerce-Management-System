package com.example.ecommerce.auth.repository;

import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(AppUser user);

    void deleteByUserId(Long userId);
}
