package com.agrolink.app.repository;

import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndIsActive(String email, boolean isActive);

    List<User> findByRole(Role role);

    List<User> findByIsActive(boolean isActive);

    List<User> findByRoleAndIsActive(Role role, boolean isActive);

    List<User> findByCreatedById(String createdById);

    long countByRole(Role role);
}