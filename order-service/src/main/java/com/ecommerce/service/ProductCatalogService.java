package com.ecommerce.service;

import com.ecommerce.exception.ProductNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Simulates a Product Catalog Service.
 *
 * In a real system this would either:
 *   a) Call a Product microservice via REST/gRPC
 *   b) Maintain a local read-model replicated via events
 *
 * The key design principle: the client sends productId + quantity only.
 * The server resolves name and price — the client NEVER decides the price.
 *
 * Available products for testing:
 *   prod-001 → Teclado Mecánico        $129.99
 *   prod-002 → Mouse Inalámbrico       $49.99
 *   prod-003 → Monitor 27"             $399.99
 *   prod-004 → Auriculares Bluetooth   $89.99
 *   prod-005 → Webcam HD               $74.99
 *   prod-006 → Hub USB-C               $39.99
 *   prod-007 → Laptop Stand            $34.99
 *   prod-008 → SSD Externo 1TB         $109.99
 *   prod-009 → Servidor Premium        $1200.00  ← siempre falla (>$1000)
 */
@Slf4j
@Service
public class ProductCatalogService {

    private static final Map<String, ProductInfo> CATALOG = Map.of(
            "prod-001", new ProductInfo("Teclado Mecánico",      new BigDecimal("129.99")),
            "prod-002", new ProductInfo("Mouse Inalámbrico",     new BigDecimal("49.99")),
            "prod-003", new ProductInfo("Monitor 27\"",          new BigDecimal("399.99")),
            "prod-004", new ProductInfo("Auriculares Bluetooth", new BigDecimal("89.99")),
            "prod-005", new ProductInfo("Webcam HD",             new BigDecimal("74.99")),
            "prod-006", new ProductInfo("Hub USB-C",             new BigDecimal("39.99")),
            "prod-007", new ProductInfo("Laptop Stand",          new BigDecimal("34.99")),
            "prod-008", new ProductInfo("SSD Externo 1TB",       new BigDecimal("109.99")),
            "prod-009", new ProductInfo("Servidor Premium",      new BigDecimal("1200.00"))
    );

    /**
     * Resolves product details by ID.
     * Throws ProductNotFoundException if the productId doesn't exist in the catalog.
     */
    public ProductInfo resolve(String productId) {
        log.debug("[ORDER-SERVICE] Resolving product | productId={}", productId);

        ProductInfo info = CATALOG.get(productId);
        if (info == null) {
            log.warn("[ORDER-SERVICE] Product not found in catalog | productId={}", productId);
            throw new ProductNotFoundException(
                    "Product not found: " + productId +
                    ". Available: prod-001 to prod-009");
        }

        log.debug("[ORDER-SERVICE] Product resolved | productId={} | name={} | price={}",
                productId, info.name(), info.price());
        return info;
    }

    public record ProductInfo(String name, BigDecimal price) {}
}
