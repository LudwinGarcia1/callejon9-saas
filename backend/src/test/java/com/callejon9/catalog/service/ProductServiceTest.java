package com.callejon9.catalog.service;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.CategoryRepository;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("createProduct con precio positivo y categoria valida guarda el producto activo")
    void createProductWithPositivePriceSucceeds() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product product = productService.createProduct("Taco Pastor", "Delicioso taco", new BigDecimal("25.50"), categoryId);

        assertThat(product.getName()).isEqualTo("Taco Pastor");
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("25.50"));
        assertThat(product.getCategoryId()).isEqualTo(categoryId);
        assertThat(product.isActive()).isTrue();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct con precio cero es permitido y guarda el producto")
    void createProductWithZeroPriceSucceeds() {
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product product = productService.createProduct("Salsa extra", "Cortesía", BigDecimal.ZERO, null);

        assertThat(product.getPrice()).isEqualTo(BigDecimal.ZERO);
        assertThat(product.isActive()).isTrue();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct con precio negativo es rechazado con BusinessRuleException")
    void createProductWithNegativePriceThrows() {
        assertThatThrownBy(() -> productService.createProduct("Taco", "Desc", new BigDecimal("-10.00"), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("El precio no puede ser negativo.");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("createProduct con categoria inexistente o de otro restaurante es rechazado")
    void createProductWithNonexistentCategoryThrows() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> productService.createProduct("Taco", "Desc", new BigDecimal("30.00"), categoryId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("La categoria especificada no existe.");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct con precio negativo es rechazado con BusinessRuleException")
    void updateProductWithNegativePriceThrows() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder().name("Taco").price(new BigDecimal("20.00")).active(true).build();
        existing.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> productService.updateProduct(productId, "Taco", "Desc", new BigDecimal("-5.00"), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("El precio no puede ser negativo.");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct con categoria inexistente es rechazado con BusinessRuleException")
    void updateProductWithNonexistentCategoryThrows() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Product existing = Product.builder().name("Taco").price(new BigDecimal("20.00")).active(true).build();
        existing.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> productService.updateProduct(productId, "Taco", "Desc", new BigDecimal("20.00"), categoryId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("La categoria especificada no existe.");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct con precio cero y categoria valida actualiza correctamente")
    void updateProductWithZeroPriceSucceeds() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder().name("Agua simple").price(new BigDecimal("15.00")).active(true).build();
        existing.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(productRepository.existsByNameAndIdNot("Agua simple", productId)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = productService.updateProduct(productId, "Agua simple", "Gratis", BigDecimal.ZERO, null);

        assertThat(updated.getPrice()).isEqualTo(BigDecimal.ZERO);
        assertThat(updated.getDescription()).isEqualTo("Gratis");
        verify(productRepository).save(existing);
    }

    @Test
    @DisplayName("updateProduct cuando el producto no existe arroja ResourceNotFoundException")
    void updateProductNotFoundThrows() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(productId, "Taco", "Desc", new BigDecimal("20.00"), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("El producto no existe.");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProduct con nombre duplicado en otro producto arroja BusinessRuleException")
    void updateProductDuplicateNameThrows() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder().name("Taco Pastor").price(new BigDecimal("25.00")).active(true).build();
        existing.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(productRepository.existsByNameAndIdNot("Taco Suadero", productId)).thenReturn(true);

        assertThatThrownBy(() -> productService.updateProduct(productId, "Taco Suadero", "Desc", new BigDecimal("25.00"), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe un producto llamado 'Taco Suadero'");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("listProducts por defecto (includeInactive=false, categoryId=null) excluye inactivos")
    void listProductsDefaultExcludesInactive() {
        Product p1 = Product.builder().name("Taco").active(true).build();
        when(productRepository.findByActiveTrueOrderByName()).thenReturn(List.of(p1));

        List<Product> result = productService.listProducts(null, false);

        assertThat(result).containsExactly(p1);
        verify(productRepository).findByActiveTrueOrderByName();
        verify(productRepository, never()).findAllByOrderByName();
    }

    @Test
    @DisplayName("listProducts con includeInactive=true y categoryId=null incluye todos los productos")
    void listProductsIncludeInactiveIncludesAll() {
        Product p1 = Product.builder().name("Taco").active(true).build();
        Product p2 = Product.builder().name("Refresco descontinuado").active(false).build();
        when(productRepository.findAllByOrderByName()).thenReturn(List.of(p1, p2));

        List<Product> result = productService.listProducts(null, true);

        assertThat(result).containsExactly(p1, p2);
        verify(productRepository).findAllByOrderByName();
        verify(productRepository, never()).findByActiveTrueOrderByName();
    }

    @Test
    @DisplayName("listProducts por categoria con includeInactive=false devuelve solo activos de esa categoria")
    void listProductsByCategoryExcludesInactive() {
        UUID categoryId = UUID.randomUUID();
        Product p1 = Product.builder().name("Coca Cola").categoryId(categoryId).active(true).build();
        when(productRepository.findByActiveTrueAndCategoryIdOrderByName(categoryId)).thenReturn(List.of(p1));

        List<Product> result = productService.listProducts(categoryId, false);

        assertThat(result).containsExactly(p1);
        verify(productRepository).findByActiveTrueAndCategoryIdOrderByName(categoryId);
        verify(productRepository, never()).findByCategoryIdOrderByName(any());
    }

    @Test
    @DisplayName("listProducts por categoria con includeInactive=true devuelve todos los productos de esa categoria")
    void listProductsByCategoryIncludesInactive() {
        UUID categoryId = UUID.randomUUID();
        Product p1 = Product.builder().name("Coca Cola").categoryId(categoryId).active(true).build();
        Product p2 = Product.builder().name("Pepsi").categoryId(categoryId).active(false).build();
        when(productRepository.findByCategoryIdOrderByName(categoryId)).thenReturn(List.of(p1, p2));

        List<Product> result = productService.listProducts(categoryId, true);

        assertThat(result).containsExactly(p1, p2);
        verify(productRepository).findByCategoryIdOrderByName(categoryId);
        verify(productRepository, never()).findByActiveTrueAndCategoryIdOrderByName(any());
    }

    @Test
    @DisplayName("setActive cambia el estado de activo de un producto")
    void setActiveUpdatesStatus() {
        UUID productId = UUID.randomUUID();
        Product existing = Product.builder().name("Taco").active(true).build();
        existing.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product deactivated = productService.setActive(productId, false);

        assertThat(deactivated.isActive()).isFalse();
        verify(productRepository).save(existing);
    }

    @Test
    @DisplayName("setActive sobre producto inexistente arroja ResourceNotFoundException")
    void setActiveThrowsWhenNotFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.setActive(productId, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("El producto no existe.");

        verify(productRepository, never()).save(any());
    }
}
