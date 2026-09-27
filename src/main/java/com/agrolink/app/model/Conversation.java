package com.agrolink.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    @Indexed
    private String orderId;

    @Indexed
    private String listingId;

    @Indexed
    private String buyerId;

    @Indexed
    private String farmerId;

    private String subject;

    @Builder.Default
    private List<String> participantIds = new ArrayList<>();

    private boolean reported;

    private String reportReason;

    private boolean moderated;

    @Indexed
    private Instant lastMessageAt;

    @CreatedDate
    private Instant createdAt;
}
