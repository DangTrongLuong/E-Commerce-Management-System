package com.example.ecommerce.product.service;

import com.example.ecommerce.product.dto.ProductCreationRequest;
import com.example.ecommerce.product.dto.ProductUpdateRequest;
import com.example.ecommerce.common.dto.PageResponse;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.user.entity.AppUser;
import com.example.ecommerce.product.entity.Product;
import com.example.ecommerce.product.enums.ProductStatus;
import com.example.ecommerce.user.enums.Role;
import com.example.ecommerce.common.exception.ConflictException;
import com.example.ecommerce.common.exception.ResourceNotFoundException;
import com.example.ecommerce.product.mapper.ProductMapper;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.common.util.SecurityUtils;
import com.example.ecommerce.common.util.SortUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ProductService {
    ProductRepository productRepository;
    ProductMapper productMapper;
    SecurityUtils securityUtils;

    @CacheEvict(value = "products", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse createProduct(ProductCreationRequest productCreationRequest) {
        boolean nameExists = productRepository
                .findByNameIgnoreCase(productCreationRequest.getName())
                .isPresent();

        if (nameExists) {
            throw new ConflictException("Product name '" + productCreationRequest.getName() + "' already exists");
        }

        Product product = productMapper.toProduct(productCreationRequest);
        if (product.getStock() == null) {
            product.setStock(0);
        }

        AppUser currentUser = securityUtils.getCurrentUser();
        product.setOwner(currentUser);

        return productMapper.toResponse(productRepository.save(product));
    }

    public PageResponse<ProductResponse> getAllProduct(
            String keyword,
            ProductStatus status,
            int page,
            int size,
            String sort) {

        int cappedSize = Math.min(Math.max(size, 1), 100);
        Sort sorting = SortUtils.buildSort(sort, "id");
        Pageable pageable = PageRequest.of(page, cappedSize, sorting);

        Specification<Product> specification = Specification.unrestricted();

        // Role-based visibility scoping
        try {
            AppUser currentUser = securityUtils.getCurrentUser();
            if (currentUser.getRole() == Role.USER) {
                specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), ProductStatus.ACTIVE));
            } else if (currentUser.getRole() == Role.PRODUCT_OWNER) {
                specification = specification.and((root, query, cb) -> cb.or(
                        cb.equal(root.get("status"), ProductStatus.ACTIVE),
                        cb.equal(root.get("owner").get("id"), currentUser.getId())
                ));
            }
        } catch (Exception e) {
            // Unauthenticated callers see ACTIVE products only
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), ProductStatus.ACTIVE));
        }

        if (keyword != null && !keyword.isBlank()) {
            specification = specification.and(
                    (root, query, cb) -> cb.like(
                            cb.lower(root.get("name")),
                            "%" + keyword.toLowerCase() + "%"));
        }

        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Product> productPage = productRepository.findAll(specification, pageable);
        Page<ProductResponse> productResponsePage = productPage.map(productMapper::toResponse);

        return PageResponse.of(productResponsePage);
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(int id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", "id", id));

        return productMapper.toResponse(product);
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public ProductResponse updateProduct(int id, ProductUpdateRequest productUpdateRequest) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", "id", id));

        verifyProductOwnership(product);

        productMapper.updateProduct(productUpdateRequest, product);
        return productMapper.toResponse(productRepository.save(product));
    }

    @CacheEvict(value = "products", key = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(int id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", "id", id));

        verifyProductOwnership(product);

        if (!product.getOrderItems().isEmpty()) {
            throw new ConflictException("Cannot delete product already associated with orders");
        }

        productRepository.delete(product);
    }

    private void verifyProductOwnership(Product product) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() == Role.PRODUCT_OWNER && product.getOwner() != null && product.getOwner().getId().equals(currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("Forbidden: Caller does not own product " + product.getId());
    }
}
