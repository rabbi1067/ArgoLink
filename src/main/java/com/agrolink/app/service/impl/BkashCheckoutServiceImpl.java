package com.agrolink.app.service.impl;

import com.agrolink.app.exception.BusinessRuleException;
import com.agrolink.app.service.BkashCheckoutService;
import com.agrolink.app.service.PaymentGatewayService.PaymentVerification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class BkashCheckoutServiceImpl implements BkashCheckoutService {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String GATEWAY = "bKash";

    private final ObjectMapper objectMapper;

    @Value("${agrolink.payment.bkash.app-key:}")
    private String appKey;

    @Value("${agrolink.payment.bkash.app-secret:}")
    private String appSecret;

    @Value("${agrolink.payment.bkash.username:}")
    private String username;

    @Value("${agrolink.payment.bkash.password:}")
    private String password;

    @Value("${agrolink.payment.bkash.base-url:https://tokenized.sandbox.bka.sh/v1.2.0-beta/tokenized/checkout}")
    private String baseUrl;

    @Value("${agrolink.payment.bkash.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Value("${agrolink.payment.bkash.sandbox:true}")
    private boolean sandbox;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean isConfigured() {
        return notBlank(appKey) && notBlank(appSecret) && notBlank(username) && notBlank(password);
    }

    @Override
    public String callbackBaseUrl() {
        String base = callbackBaseUrl == null ? "" : callbackBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    @Override
    public CheckoutSession createCheckout(String orderId, BigDecimal amount) {
        requireConfigured();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Order total is missing or not positive", 409);
        }

        String token = grantToken();
        String callbackUrl = callbackBaseUrl() + "/api/v1/orders/payment/bkash/callback?orderId=" + orderId;
        String body = """
                {
                  "amount": "%s",
                  "currency": "BDT",
                  "intent": "sale",
                  "merchantInvoiceNumber": "%s",
                  "callbackURL": "%s"
                }
                """.formatted(amount.setScale(2, RoundingMode.HALF_UP), orderId, callbackUrl);

        JsonNode response = post(token, baseUrl + "/tokenized/checkout/create", body);
        String paymentId = response.path("paymentID").asText("");
        String bkashUrl = response.path("bkashURL").asText("");
        if (!"success".equalsIgnoreCase(response.path("status").asText("")) || paymentId.isBlank() || bkashUrl.isBlank()) {
            throw new BusinessRuleException(
                    "bKash could not start the payment: " + firstMessage(response), 422);
        }
        log.info("bKash checkout created for order {} (paymentID {})", orderId, paymentId);
        return new CheckoutSession(paymentId, bkashUrl);
    }

    @Override
    public PaymentVerification verifyPayment(String paymentId, String orderId, BigDecimal expectedAmount) {
        if (paymentId == null || paymentId.isBlank()) {
            return PaymentVerification.failure(GATEWAY, "bKash did not return a payment id");
        }
        if (!isConfigured()) {
            return PaymentVerification.failure(GATEWAY,
                    "bKash credentials are not configured, so the payment cannot be verified");
        }

        String token = grantToken();
        String body = "{\"paymentID\":\"" + paymentId.trim() + "\"}";
        JsonNode response = post(token, baseUrl + "/tokenized/checkout/payment/verify", body);

        String status = response.path("status").asText("");
        if (!"completed".equalsIgnoreCase(status) && !"success".equalsIgnoreCase(status)) {
            return PaymentVerification.failure(GATEWAY,
                    "bKash did not complete the payment (" + firstMessage(response) + ")");
        }

        String merchantInvoice = response.path("merchantInvoiceNumber").asText(orderId);
        if (orderId != null && !orderId.equals(merchantInvoice)) {
            return PaymentVerification.failure(GATEWAY,
                    "bKash payment belongs to a different invoice (" + merchantInvoice + ")");
        }

        BigDecimal paid = amountOf(response.path("amount").asText(null));
        if (paid == null || expectedAmount == null
                || paid.subtract(expectedAmount).abs().compareTo(AMOUNT_TOLERANCE) > 0) {
            return PaymentVerification.failure(GATEWAY, "Amount mismatch: bKash charged " + paid
                    + " but the order total is " + expectedAmount);
        }

        String trxId = response.path("trxID").asText("");
        log.info("bKash confirmed payment {} for order {} (trxID {})", paymentId, orderId, trxId);
        return PaymentVerification.success(GATEWAY + (sandbox ? " Sandbox" : ""), true,
                "Confirmed by bKash (transaction " + trxId + ")");
    }

    private String grantToken() {
        if (!isConfigured()) {
            throw new BusinessRuleException(
                    "bKash payment is not configured. Set BKASH_APP_KEY, BKASH_APP_SECRET, BKASH_USERNAME and BKASH_PASSWORD.", 503);
        }
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        String body = "{\"app_key\":\"" + appKey + "\",\"app_secret\":\"" + appSecret + "\"}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/tokenized/checkout/token/granttype"))
                .timeout(TIMEOUT)
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = send(request, "grant a bKash token");
        String idToken = response.path("id_token").asText("");
        if (idToken.isBlank()) {
            throw new BusinessRuleException("bKash refused the API credentials: " + firstMessage(response), 502);
        }
        return idToken;
    }

    private JsonNode post(String token, String url, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", token)
                .header("X-APP-Key", appKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request, "call the bKash checkout API");
    }

    private JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new BusinessRuleException(
                        "Could not " + action + ": bKash returned HTTP " + response.statusCode(), 502);
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("The bKash request was interrupted", 502);
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("bKash request failed: {}", ex.toString());
            throw new BusinessRuleException(
                    "Could not " + action + ". Check the bKash credentials and the sandbox base URL.", 502);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessRuleException(
                    "bKash payment is not configured. Set BKASH_APP_KEY, BKASH_APP_SECRET, BKASH_USERNAME and BKASH_PASSWORD.", 503);
        }
    }

    private String firstMessage(JsonNode response) {
        String message = response.path("statusMessage").asText("");
        if (message.isBlank()) {
            message = response.path("message").asText("");
        }
        return message.isBlank() ? "no reason given by bKash" : message;
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

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
