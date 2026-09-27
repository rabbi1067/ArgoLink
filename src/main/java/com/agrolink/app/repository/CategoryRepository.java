package com.agrolink.app.repository;

import com.agrolink.app.model.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategoryRepository extends MongoRepository<Category, String> {

    List<Category> findAllByOrderByDisplayOrderAsc();

    boolean existsByNameIgnoreCase(String name);
}