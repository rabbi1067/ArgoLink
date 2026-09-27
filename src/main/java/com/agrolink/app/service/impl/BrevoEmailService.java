package com.agrolink.app.service.impl;

import com.agrolink.app.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


@Slf4j
@Service
@RequiredArgsConstructor
public class BrevoEmailService implements EmailService {

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${agrolink.brevo.api-key:}")
    private String apiKey;

    @Value("${agrolink.brevo.sender-email:}")
    private String senderEmail;

    @Value("${agrolink.brevo.sender-name:AgroLink}")
    private String senderName;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void sendPasswordResetCode(String toEmail, String toName, String code) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not set - password reset code for {} is: {}", toEmail, code);
            return;
        }
        String subject = "Your AgroLink password reset code";
        String html = "<p>Hello " + escape(toName) + ",</p>"
                + "<p>Your AgroLink password reset code is:</p>"
                + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:4px;\">" + code + "</p>"
                + "<p>This code expires in 10 minutes. If you did not ask for it, ignore this email.</p>";
        String body = "{"
                + "\"sender\":{\"email\":" + json(senderEmail) + ",\"name\":" + json(senderName) + "},"
                + "\"to\":[{\"email\":" + json(toEmail) + ",\"name\":" + json(toName) + "}],"
                + "\"subject\":" + json(subject) + ","
                + "\"htmlContent\":" + json(html)
                + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_SEND_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("accept", "application/json")
                    .header("api-key", apiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("Brevo rejected the reset email for {} (status {}): {}",
                        toEmail, response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("Could not send the reset email to {}", toEmail, ex);
        }
    }

    private static String json(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
