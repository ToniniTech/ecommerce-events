package com.ecommerce.mapper;

import com.ecommerce.controller.dto.CreateProductRequest;
import com.ecommerce.controller.dto.ProductResponse;
import com.ecommerce.controller.dto.UpdateProductRequest;
import com.ecommerce.domain.Product;
import org.springframework.stereotype.Component;

/**
 * Maps between the Product entity and its DTOs.
 * Keeping the mapping in one place prevents the JPA entity from leaking into the
 * web layer and keeps the service focused on business logic.
 */
@Component
public class ProductMapper {

    /** Build a new entity from a create request. Defaults {@code active} to true when omitted. */
    public Product toEntity(CreateProductRequest createProductRequest) {
        return Product.builder()
                .productId(createProductRequest.productId())
                .name(createProductRequest.name())
                .price(createProductRequest.price())
                .stock(createProductRequest.stock())
                .active(createProductRequest.active())
                .build();
    }

    /**
     * Apply a full update to an existing managed entity.
     * productId and stock are intentionally not touched here (immutable key / managed
     * only via the stock endpoint).
     */
    public void applyUpdate(Product product, UpdateProductRequest request) {
        product.setName(request.name());
        product.setPrice(request.price());
        product.setActive(request.active());
    }

    /** Convert an entity into its outbound representation. */
    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.isActive()
        );
    }
}
