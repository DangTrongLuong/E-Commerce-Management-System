package com.example.ecommerce.util;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@FieldDefaults( level = AccessLevel.PRIVATE)
public class JwtUtil {
    @Value("${jwt.secret}")
    String jwtSecret;

    @Value("${jwt.expiration}")
    long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    long refreshExpiration;



}
