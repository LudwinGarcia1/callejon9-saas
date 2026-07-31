package com.callejon9.catalog.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
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

    /**
     * Corrige nombre, descripcion, precio o categoria de un producto ya
     * creado. El precio nuevo NUNCA toca las ordenes existentes: cada linea
     * de orden guarda su propia copia del precio al momento de agregarse
     * ({@code OrderService.toOrderItem}), asi que este cambio solo afecta a
     * las ordenes que se abran de aqui en adelante.
     */
    @Transactional
    public Product updateProduct(UUID productId, String name, String description,
                                  BigDecimal price, UUID categoryId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe."));

        if (productRepository.existsByNameAndIdNot(name, productId)) {
            throw new BusinessRuleException(
                    "Ya existe un producto llamado '" + name + "'.");
        }

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategoryId(categoryId);
        return productRepository.save(product);
    }

    /**
     * Da de alta o de baja un producto. La baja es siempre logica (active =
     * false): el producto queda referenciado por las lineas de las ordenes
     * que ya lo incluyeron, y borrarlo perderia esa atribucion. Un producto
     * dado de baja solo desaparece del catalogo ({@link #listProducts});
     * las ordenes que ya lo tienen no se ven afectadas.
     */
    @Transactional
    public Product setActive(UUID productId, boolean active) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe."));

        product.setActive(active);
        return productRepository.save(product);
    }
}
