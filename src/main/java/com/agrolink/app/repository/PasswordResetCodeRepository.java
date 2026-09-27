package com.agrolink.app.repository;

import com.agrolink.app.model.PasswordResetCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository extends MongoRepository<PasswordResetCode, String> {

    Optional<PasswordResetCode> findByEmail(String email);

    void deleteByEmail(String email);
}
