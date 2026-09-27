package com.agrolink.app.service;


public interface EmailService {

    void sendPasswordResetCode(String toEmail, String toName, String code);
}
