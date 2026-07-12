package com.ecommerce.client;

import com.ecommerce.exception.ProductNotAvailableException;
import com.ecommerce.exception.ProductNotFoundException;
import com.ecommerce.exception.ProductServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Synchronous client for product-service (the intentional REST-only service).
 * Resolves the authoritative name and price of a product from its business key,
 * so the client of order-service can never dictate the price.
 */
@Slf4j
@Component
public class ProductCatalogClient {

    private final RestClient productRestClient;

    // Explicit constructor (no Lombok) so we can @Qualifier the specific RestClient
    // bean unambiguously, even if more HTTP clients are added later.
    public ProductCatalogClient(@Qualifier("productRestClient") RestClient productRestClient) {
        this.productRestClient = productRestClient;
    }

    /**
     * Resolve a product by its business key.
     *
     * @throws ProductNotFoundException        (422) if product-service returns 404
     * @throws ProductNotAvailableException    (422) if the product exists but is inactive
     * @throws ProductServiceUnavailableException (503) if product-service is unreachable / 5xx
     */
    public ProductInfo resolve(String productId) {
        log.debug("[ORDER-SERVICE] Resolving product from catalog | productId={}", productId);

        try {
            ProductResponse product = productRestClient.get()
                    .uri("/api/products/{productId}", productId)
                    .retrieve()
                    // 404 → business error: the product does not exist in the catalog.
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new ProductNotFoundException("Product not found in catalog: " + productId);
                    })
                    // 5xx → downstream failure, not the caller's fault.
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new ProductServiceUnavailableException(
                                "product-service returned " + response.getStatusCode()
                                        + " for productId=" + productId);
                    })
                    .body(ProductResponse.class);

            if (product == null) {
                throw new ProductServiceUnavailableException(
                        "Empty response from product-service for productId=" + productId);
            }

            // Business rule: a soft-deleted / hidden product cannot be ordered.
            if (!product.active()) {
                throw new ProductNotAvailableException("Product is not available for purchase: " + productId);
            }

            return new ProductInfo(product.productId(), product.name(), product.price());

        } catch (ResourceAccessException ex) {
            // I/O layer: connection refused, DNS failure, connect/read timeout.
            throw new ProductServiceUnavailableException(
                    "product-service is unreachable (productId=" + productId + ")", ex);
        }
    }
}
