package com.streamtube.infrastructure.persistence.repository;

import com.streamtube.infrastructure.persistence.entity.CategoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {

  List<CategoryEntity> findAllByOrderByNameAsc();
}
