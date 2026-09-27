package com.agrolink.app.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stored_files")
public class StoredFile {

    @Id
    private String id;

    private String fileName;

    private String contentType;

    private long size;

    /** Email of the uploader (the authentication name). */
    private String uploadedBy;

    @ToString.Exclude
    private byte[] data;

    private Instant createdAt;
}