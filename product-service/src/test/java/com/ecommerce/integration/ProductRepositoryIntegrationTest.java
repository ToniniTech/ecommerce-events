package com.ecommerce.integration;

import com.ecommerce.domain.ProductRepository;
import com.ecommerce.service.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import static org.assertj.core.api.Assertions.assertThat;

public class ProductRepositoryIntegrationTest  extends IntegrationTestBase {
        @Autowired
        private ProductRepository productRepository;

        @Test
        void contextsLoadsAndConnectsToDatabase(){
                assertThat(productRepository.count()).isGreaterThan(0);
        }


}
