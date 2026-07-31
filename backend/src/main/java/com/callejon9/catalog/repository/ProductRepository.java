package com.callejon9.catalog.repository;

import com.callejon9.catalog.domain.Product;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrueOrderByName();

    List<Product> findByActiveTrueAndCategoryIdOrderByName(UUID categoryId);

    /** Para includeInactive=true: catalogo completo, sin filtrar por estado. */
    List<Product> findAllByOrderByName();

    /** Para includeInactive=true con categoria: sin filtrar por estado. */
    List<Product> findByCategoryIdOrderByName(UUID categoryId);

    /** Para renombrar un producto existente sin chocar contra si mismo. */
    boolean existsByNameAndIdNot(String name, UUID id);
}
