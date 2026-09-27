package com.agrolink.app.dto;

public record UploadResponse(String id, String url, String contentType, long size) {
}