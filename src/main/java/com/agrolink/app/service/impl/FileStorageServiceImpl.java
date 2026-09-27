package com.agrolink.app.service.impl;

import com.agrolink.app.dto.UploadResponse;
import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.exception.ResourceNotFoundException;
import com.agrolink.app.model.StoredFile;
import com.agrolink.app.repository.StoredFileRepository;
import com.agrolink.app.service.FileStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/%s/image/upload";

    private final StoredFileRepository fileRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public FileStorageServiceImpl(StoredFileRepository fileRepository,
                                   ObjectMapper objectMapper,
                                   @Value("${agrolink.cloudinary.cloud-name:}") String cloudName,
                                   @Value("${agrolink.cloudinary.api-key:}") String apiKey,
                                   @Value("${agrolink.cloudinary.api-secret:}") String apiSecret,
                                   @Value("${agrolink.cloudinary.produce-folder:agrolink/produce}") String folder) {
        this.fileRepository = fileRepository;
        this.objectMapper = objectMapper;
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.folder = folder == null || folder.isBlank() ? "agrolink/produce" : folder.trim();

        if (cloudinaryEnabled()) {
            log.info("Image storage: Cloudinary (cloud '{}', folder '{}')", this.cloudName, this.folder);
        } else {
            log.warn("Image storage: MongoDB fallback. Set the agrolink.cloudinary credentials "
                    + "(CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET) to use Cloudinary.");
        }
    }

    @Override
    public UploadResponse store(MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose an image to upload", 400);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("Image is too large (maximum 2 MB)", 413);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("Could not read the uploaded file", 400);
        }

        // Trust the file's real content, never the client-supplied Content-Type or extension.
        String contentType = detectImageType(bytes);
        if (contentType == null) {
            throw new BusinessRuleException("Only JPEG, PNG or WebP images are allowed", 415);
        }

        String fileName = safeName(file.getOriginalFilename());
        return cloudinaryEnabled()
                ? uploadToCloudinary(bytes, contentType, fileName)
                : storeInMongo(bytes, contentType, fileName, uploadedBy);
    }

    @Override
    public StoredFile load(String id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Image", "id", id));
    }

    // ------------------------------------------------------------------ Cloudinary

    private boolean cloudinaryEnabled() {
        return !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    private UploadResponse uploadToCloudinary(byte[] bytes, String contentType, String fileName) {
        long timestamp = Instant.now().getEpochSecond();

        // Signed upload: SHA-1 of the alphabetically sorted params (except file/api_key) + API secret.
        String signature = sha1Hex("folder=" + folder + "&timestamp=" + timestamp + apiSecret);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("api_key", apiKey);
        fields.put("timestamp", String.valueOf(timestamp));
        fields.put("folder", folder);
        fields.put("signature", signature);

        String boundary = "----AgroLink" + UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] body = multipartBody(boundary, fields, fileName, contentType, bytes);
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(UPLOAD_URL, cloudName)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Cloudinary upload failed: HTTP {} {}", response.statusCode(), abbreviate(response.body()));
                throw new BusinessRuleException("The image service rejected the upload. Please try again.", 502);
            }

            JsonNode json = objectMapper.readTree(response.body());
            String secureUrl = json.path("secure_url").asText(null);
            if (secureUrl == null || !secureUrl.startsWith("https://")) {
                log.warn("Cloudinary response had no secure_url");
                throw new BusinessRuleException("The image service returned an unexpected response", 502);
            }
            return new UploadResponse(json.path("public_id").asText(""), optimize(secureUrl), contentType, bytes.length);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("Upload was interrupted. Please try again.", 503);
        } catch (IOException ex) {
            log.warn("Cloudinary upload I/O error: {}", ex.toString());
            throw new BusinessRuleException("The image service is unavailable right now. Please try again.", 502);
        }
    }

    private static String optimize(String secureUrl) {
        String marker = "/image/upload/";
        return secureUrl.contains(marker)
                ? secureUrl.replace(marker, marker + "f_auto,q_auto/")
                : secureUrl;
    }

    private static byte[] multipartBody(String boundary, Map<String, String> fields, String fileName,
                                        String fileType, byte[] data) throws IOException {
        String nl = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 1024);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            out.write(("--" + boundary + nl
                    + "Content-Disposition: form-data; name=\"" + field.getKey() + "\"" + nl + nl
                    + field.getValue() + nl).getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + nl
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + nl
                + "Content-Type: " + fileType + nl + nl).getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write((nl + "--" + boundary + "--" + nl).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is not available", ex);
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    // ------------------------------------------------------------------ MongoDB fallback

    private UploadResponse storeInMongo(byte[] bytes, String contentType, String fileName, String uploadedBy) {
        StoredFile saved = fileRepository.save(StoredFile.builder()
                .fileName(fileName)
                .contentType(contentType)
                .size(bytes.length)
                .uploadedBy(uploadedBy)
                .data(bytes)
                .createdAt(Instant.now())
                .build());
        return new UploadResponse(saved.getId(), "/api/v1/files/" + saved.getId(), contentType, bytes.length);
    }

    // ------------------------------------------------------------------ validation helpers

    private static String detectImageType(byte[] b) {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "image";
        }
        String cleaned = original.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}