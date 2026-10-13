package com.example.ecommerce.cart.repository;

import com.example.ecommerce.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Integer productId);

    void deleteByCartIdAndProductId(Long cartId, Integer productId);

    void deleteAllByCartId(Long cartId);
}
