package com.callejon9.catalog.service;

import com.callejon9.catalog.domain.Category;
import com.callejon9.catalog.repository.CategoryRepository;
import com.callejon9.shared.error.BusinessRuleException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public Category createCategory(String name, int sortOrder) {
        if (categoryRepository.existsByName(name)) {
            throw new BusinessRuleException(
                    "Ya existe una categoria llamada '" + name + "'.");
        }

        return categoryRepository.save(Category.builder()
                .name(name)
                .sortOrder(sortOrder)
                .build());
    }
}
