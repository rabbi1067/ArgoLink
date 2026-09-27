package com.agrolink.app.service;

public interface CloudinaryService {

    record UploadResult(String publicId, String secureUrl, String format) {
    }


    UploadResult uploadImage(byte[] content, String contentType, String folder);

    UploadResult uploadAvatar(byte[] content, String contentType);


    UploadResult uploadProduceImage(byte[] content, String contentType);


    String signedUrl(String publicId, String format);
}