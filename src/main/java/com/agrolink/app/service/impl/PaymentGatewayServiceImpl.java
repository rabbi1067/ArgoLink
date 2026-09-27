package com.agrolink.app.service.impl;

import com.agrolink.app.model.PaymentMethod;
import com.agrolink.app.service.PaymentGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayServiceImpl implements PaymentGatewayService {

    private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9_-]{6,40}$");
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;

    @Value("${agrolink.payment.sslcommerz.store-id:}")
    private String storeId;

    @Value("${agrolink.payment.sslcommerz.store-password:}")
    private String storePassword;

    @Value("${agrolink.payment.sslcommerz.validation-url:https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php}")
    private String validationUrl;

    @Value("${agrolink.payment.require-gateway-verification:false}")
    private boolean requireGatewayVerification;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public PaymentVerification verify(PaymentMethod method, String transactionId, BigDecimal expectedAmount, String reference) {
        if (method == null) {
            return PaymentVerification.failure("Unknown", "Payment method is required");
        }
        String gateway = gatewayLabel(method);
        String trimmedReference = transactionId == null ? "" : transactionId.trim();
        if (!REFERENCE.matcher(trimmedReference).matches()) {
            return PaymentVerification.failure(gateway,
                    "Transaction id must be 6-40 characters using letters, digits, '-' or '_'");
        }
        if (expectedAmount == null || expectedAmount.signum() <= 0) {
            return PaymentVerification.failure(gateway, "Order total is missing or not positive");
        }

        if (method == PaymentMethod.SSLCOMMERZ && hasSslCredentials()) {
            return verifyWithSslCommerz(trimmedReference, expectedAmount, reference);
        }
        if (requireGatewayVerification) {
            return PaymentVerification.failure(gateway,
                    "Live gateway verification is required but no credentials are configured for " + gateway);
        }
        return PaymentVerification.success(gateway, false,
                gateway + " sandbox accepted the payment on local sandbox rules (no live gateway call was made)");
    }

    private PaymentVerification verifyWithSslCommerz(String transactionId, BigDecimal expectedAmount, String reference) {
        String url = validationUrl
                + "?val_id=" + encode(transactionId)
                + "&store_id=" + encode(storeId)
                + "&store_passwd=" + encode(storePassword)
                + "&format=json";
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return PaymentVerification.failure("SSLCommerz Sandbox",
                        "SSLCommerz sandbox returned HTTP " + response.statusCode());
            }
            JsonNode body = objectMapper.readTree(response.body());
            String status = body.path("status").asText("");
            if (!"VALID".equalsIgnoreCase(status) && !"COMPLETED".equalsIgnoreCase(status)) {
                String reason = body.path("error").asText("transaction not valid");
                return PaymentVerification.failure("SSLCommerz Sandbox", "SSLCommerz rejected the payment: " + reason);
            }
            BigDecimal gatewayAmount = amountOf(body.path("amount").asText(null));
            if (gatewayAmount != null && gatewayAmount.subtract(expectedAmount).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
                return PaymentVerification.failure("SSLCommerz Sandbox",
                        "Amount mismatch: gateway charged " + gatewayAmount + " but the order total is " + expectedAmount);
            }
            String gatewayTranId = body.path("tran_id").asText(transactionId);
            log.info("SSLCommerz sandbox confirmed transaction {} for order {}", gatewayTranId, reference);
            return PaymentVerification.success("SSLCommerz Sandbox", true,
                    "Confirmed by the SSLCommerz developer sandbox (transaction " + gatewayTranId + ")");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PaymentVerification.failure("SSLCommerz Sandbox", "Payment verification was interrupted");
        } catch (Exception ex) {
            log.warn("SSLCommerz sandbox verification failed for order {}: {}", reference, ex.toString());
            return PaymentVerification.failure("SSLCommerz Sandbox",
                    "Could not reach the SSLCommerz sandbox, the payment was not verified");
        }
    }

    private boolean hasSslCredentials() {
        return storeId != null && !storeId.isBlank() && storePassword != null && !storePassword.isBlank();
    }

    @Override
    public boolean isSslCommerzConfigured() {
        return hasSslCredentials();
    }

    @Override
    public boolean isVerificationRequired() {
        return requireGatewayVerification;
    }

    private BigDecimal amountOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String gatewayLabel(PaymentMethod method) {
        return switch (method) {
            case BKASH -> "bKash Sandbox";
            case NAGAD -> "Nagad Sandbox";
            case CARD -> "Card Sandbox";
            case SSLCOMMERZ -> "SSLCommerz Sandbox";
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
