package com.agrolink.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "password_reset_codes")
public class PasswordResetCode {

    @Id
    private String id;

    @Indexed
    private String email;

    private String codeHash;

    @Indexed(expireAfterSeconds = 600)
    private Instant expiresAt;

    @Builder.Default
    private int attemptsLeft = 5;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
