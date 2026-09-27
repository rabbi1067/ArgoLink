package com.agrolink.app.service.impl;

import com.agrolink.app.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
@Primary
public class EmailJsEmailService implements EmailService {

    private static final String EMAILJS_SEND_URL = "https://api.emailjs.com/api/v1.0/email/send";

    @Value("${agrolink.emailjs.service-id:}")
    private String serviceId;

    @Value("${agrolink.emailjs.template-id:}")
    private String templateId;

    @Value("${agrolink.emailjs.public-key:}")
    private String publicKey;

    @Value("${agrolink.emailjs.private-key:}")
    private String privateKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public void sendPasswordResetCode(String toEmail, String toName, String code) {
        if (serviceId == null || serviceId.isBlank()
                || templateId == null || templateId.isBlank()
                || publicKey == null || publicKey.isBlank()) {
            log.warn("EmailJS is not configured - password reset code for {} is: {}", toEmail, code);
            return;
        }
        String safeName = toName == null || toName.isBlank() ? "there" : toName;
        String subject = "Your AgroLink password reset code";
        String body = "Hello " + safeName + ",\n\n"
                + "Your AgroLink password reset code is: " + code + "\n\n"
                + "This code expires in 10 minutes. "
                + "If you did not ask for it, ignore this email.";
        String payload = "{"
                + "\"service_id\":" + json(serviceId) + ","
                + "\"template_id\":" + json(templateId) + ","
                + "\"user_id\":" + json(publicKey) + ","
                + "\"accessToken\":" + json(privateKey == null ? "" : privateKey) + ","
                + "\"template_params\":{"
                + "\"user_email\":" + json(toEmail) + ","
                + "\"subject\":" + json(subject) + ","
                + "\"body\":" + json(body)
                + "}"
                + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EMAILJS_SEND_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("EmailJS rejected the reset email for {} (status {}): {}",
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
}
