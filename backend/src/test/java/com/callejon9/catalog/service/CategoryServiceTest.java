package com.callejon9.catalog.service;

import com.callejon9.catalog.domain.Category;
import com.callejon9.catalog.repository.CategoryRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
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
@DisplayName("UT · CategoryService")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("listCategories devuelve las categorias ordenadas por sortOrder y nombre")
    void listCategoriesReturnsOrderedCategories() {
        Category cat1 = Category.builder().name("Bebidas").sortOrder(1).build();
        cat1.setId(UUID.randomUUID());
        Category cat2 = Category.builder().name("Entradas").sortOrder(2).build();
        cat2.setId(UUID.randomUUID());
        Category cat3 = Category.builder().name("Postres").sortOrder(3).build();
        cat3.setId(UUID.randomUUID());

        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc())
                .thenReturn(List.of(cat1, cat2, cat3));

        List<Category> result = categoryService.listCategories();

        assertThat(result).containsExactly(cat1, cat2, cat3);
        verify(categoryRepository).findAllByOrderBySortOrderAscNameAsc();
    }

    @Test
    @DisplayName("createCategory guarda la categoria cuando el nombre no existe")
    void createCategorySucceedsWhenNameDoesNotExist() {
        when(categoryRepository.existsByName("Tacos")).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.createCategory("Tacos", 10);

        assertThat(created.getName()).isEqualTo("Tacos");
        assertThat(created.getSortOrder()).isEqualTo(10);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("createCategory arroja BusinessRuleException cuando el nombre ya existe")
    void createCategoryThrowsWhenNameExists() {
        when(categoryRepository.existsByName("Tacos")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory("Tacos", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe una categoria llamada 'Tacos'");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCategory actualiza el nombre y orden de la categoria")
    void updateCategorySucceeds() {
        UUID id = UUID.randomUUID();
        Category existing = Category.builder().name("Bebida").sortOrder(5).build();
        existing.setId(id);

        when(categoryRepository.findById(id)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameAndIdNot("Bebidas Frias", id)).thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category updated = categoryService.updateCategory(id, "Bebidas Frias", 2);

        assertThat(updated.getName()).isEqualTo("Bebidas Frias");
        assertThat(updated.getSortOrder()).isEqualTo(2);
        verify(categoryRepository).save(existing);
    }

    @Test
    @DisplayName("updateCategory arroja ResourceNotFoundException si la categoria no existe")
    void updateCategoryThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(id, "Platos", 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("La categoria no existe.");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCategory arroja BusinessRuleException si el nuevo nombre ya pertenece a otra categoria")
    void updateCategoryThrowsWhenDuplicateNameOnOtherCategory() {
        UUID id = UUID.randomUUID();
        Category existing = Category.builder().name("Entrada").sortOrder(1).build();
        existing.setId(id);

        when(categoryRepository.findById(id)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameAndIdNot("Postres", id)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(id, "Postres", 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe una categoria llamada 'Postres'");

        verify(categoryRepository, never()).save(any());
    }
}
