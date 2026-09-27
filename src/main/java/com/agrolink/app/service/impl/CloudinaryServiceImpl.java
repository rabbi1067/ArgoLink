package com.agrolink.app.service.impl;

import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.service.CloudinaryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final String UPLOAD_API = "https://api.cloudinary.com/v1_1/%s/image/upload";
    private static final String DELIVERY_BASE = "https://res.cloudinary.com/%s/image/authenticated/%s";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agrolink.cloudinary.cloud-name}")
    private String cloudName;

    @Value("${agrolink.cloudinary.api-key}")
    private String apiKey;

    @Value("${agrolink.cloudinary.api-secret}")
    private String apiSecret;

    @Value("${agrolink.cloudinary.avatar-folder:agrolink/avatars}")
    private String avatarFolder;

    @Value("${agrolink.cloudinary.produce-folder:agrolink/produce}")
    private String produceFolder;

    @Override
    public UploadResult uploadImage(byte[] content, String contentType, String folder) {
        validateImage(content, contentType);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String format = imageExtension(contentType);
        // Base64 data URI keeps the whole request form-encoded and the upload
        // signature covers the same URL-encoded serializer (no multipart drift).
        String fileParam = "data:" + contentType + ";base64,"
                + Base64.getEncoder().encodeToString(content);

        TreeMap<String, String> signable = new TreeMap<>();
        signable.put("folder", folder);
        signable.put("timestamp", timestamp);
        signable.put("type", "authenticated");
        String signature = hexSign(signable);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("file", fileParam);
        form.put("folder", folder);
        form.put("timestamp", timestamp);
        form.put("type", "authenticated");
        form.put("api_key", apiKey);
        form.put("signature", signature);

        HttpRequest request = HttpRequest.newBuilder(URI.create(UPLOAD_API.formatted(cloudName)))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(urlEncode(form)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            String publicId = root.path("public_id").asText(null);
            String secureUrl = root.path("secure_url").asText(null);
            if (publicId == null || publicId.isBlank()) {
                log.warn("Cloudinary upload failed with HTTP {}: {}", response.statusCode(), truncate(response.body()));
                String message = root.path("error").path("message").asText("Image upload failed");
                throw new BusinessRuleException(message, 502);
            }
            return new UploadResult(publicId, secureUrl, format);
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Cloudinary upload interrupted", ex);
            throw new BusinessRuleException("Image upload failed", 502);
        } catch (Exception ex) {
            log.warn("Cloudinary upload error", ex);
            throw new BusinessRuleException("Image upload failed", 502);
        }
    }

    @Override
    public UploadResult uploadAvatar(byte[] content, String contentType) {
        return uploadImage(content, contentType, avatarFolder);
    }

    @Override
    public UploadResult uploadProduceImage(byte[] content, String contentType) {
        return uploadImage(content, contentType, produceFolder);
    }

    @Override
    public String signedUrl(String publicId, String format) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        boolean hasFormat = format != null && !format.isBlank();
        String assetPath = hasFormat ? publicId + "." + format : publicId;
        return DELIVERY_BASE.formatted(cloudName, "s--" + deliverySignature(assetPath) + "--/" + assetPath);
    }

    private void validateImage(byte[] content, String contentType) {
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BusinessRuleException("Only image files are allowed", 400);
        }
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Image file is empty", 400);
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw new BusinessRuleException("Image must be 5 MB or smaller", 400);
        }
    }


    private String deliverySignature(String assetPath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((assetPath + apiSecret).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }


    private String hexSign(Map<String, String> paramsToSign) {
        String canonical = paramsToSign.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return sha1Hex(canonical + apiSecret);
    }

    private String imageExtension(String contentType) {
        String lower = contentType == null ? "" : contentType.toLowerCase();
        if (lower.contains("jpeg")) {
            return "jpg";
        }
        if (lower.contains("png")) {
            return "png";
        }
        if (lower.contains("webp")) {
            return "webp";
        }
        if (lower.contains("gif")) {
            return "gif";
        }
        return "png";
    }

    private String urlEncode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sha1Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not available", ex);
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }
}