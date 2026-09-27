package com.agrolink.app.repository;

import com.agrolink.app.model.StoredFile;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoredFileRepository extends MongoRepository<StoredFile, String> {
}