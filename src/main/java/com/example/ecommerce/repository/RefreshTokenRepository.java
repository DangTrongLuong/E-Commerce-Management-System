package com.example.ecommerce.repository;

import com.example.ecommerce.entity.AppUser;
import com.example.ecommerce.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(AppUser user);

    void deleteByUserId(Long userId);
}