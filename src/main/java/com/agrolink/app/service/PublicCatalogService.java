package com.agrolink.app.service;

import com.agrolink.app.config.CacheConfig;
import com.agrolink.app.model.Category;
import com.agrolink.app.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private final CategoryRepository categoryRepository;

    @Cacheable(cacheNames = CacheConfig.CACHE_CATEGORIES)
    public List<Category> getCategories() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc();
    }
}