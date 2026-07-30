package com.callejon9.catalog.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts(UUID categoryId) {
        if (categoryId != null) {
            return productRepository.findByActiveTrueAndCategoryIdOrderByName(categoryId);
        }
        return productRepository.findByActiveTrueOrderByName();
    }

    @Transactional
    public Product createProduct(String name, String description, BigDecimal price,
                                  UUID categoryId) {
        return productRepository.save(Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .categoryId(categoryId)
                .active(true)
                .build());
    }
}
