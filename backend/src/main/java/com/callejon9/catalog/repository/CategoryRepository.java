package com.callejon9.catalog.repository;

import com.callejon9.catalog.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByName(String name);

    List<Category> findAllByOrderBySortOrderAscNameAsc();
}
