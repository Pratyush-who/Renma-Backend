package com.example.renma.service.auth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Service
public class ObligrMobileOtpProvider implements MobileOtpProvider {

    private final WebClient webClient;
    private final ObligrOtpProperties properties;

    public ObligrMobileOtpProvider(WebClient.Builder webClientBuilder, ObligrOtpProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public String sendOtp(String mobileNumber) {
        ensureConfigured();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile_no", mobileNumber);
        form.add("type", "sms");
        addIfPresent(form, "sender_id", properties.getSenderId());
        addIfPresent(form, "header_name", properties.getHeaderName());
        addIfPresent(form, "message", properties.getMessage());
        addIfPresent(form, "dlt_template_id", properties.getDltTemplateId());
        addIfPresent(form, "dlt_pe_id", properties.getDltPeId());
        addIfPresent(form, "caller_id", properties.getCallerId());
        addIfPresent(form, "email_id", properties.getEmailId());
        form.add("expire_time", String.valueOf(properties.getExpireTime()));
        form.add("otp_length", String.valueOf(properties.getOtpLength()));
        form.add("is_unicode", "0");

        Map<String, Object> response = postForm("/api_v2/tfa/send", form);
        String verifyKey = textAt(response, "verify_key", "verifyKey", "key", "data.verify_key", "data.verifyKey");
        if (verifyKey == null) {
            throw new IllegalStateException("Obligr did not return a verify key.");
        }
        return verifyKey;
    }

    @Override
    public boolean verifyOtp(String mobileNumber, String verifyKey, String otp) {
        ensureConfigured();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile_no", mobileNumber);
        form.add("verify_key", verifyKey);
        form.add("otp", otp);

        Map<String, Object> response = postForm("/api_v2/tfa/verify", form);
        Boolean success = booleanAt(response, "success", "status", "verified", "data.success", "data.status", "data.verified");
        if (success != null) {
            return success;
        }

        String message = textAt(response, "message", "status", "data.message");
        return message != null && message.toLowerCase().contains("success");
    }

    private Map<String, Object> postForm(String uri, MultiValueMap<String, String> form) {
        try {
            return webClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();
        } catch (WebClientResponseException e) {
            throw new IllegalStateException("Obligr OTP request failed: " + e.getStatusCode(), e);
        }
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Obligr OTP provider is disabled.");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Obligr API key is not configured.");
        }
    }

    private void addIfPresent(MultiValueMap<String, String> form, String key, String value) {
        if (value != null && !value.isBlank()) {
            form.add(key, value);
        }
    }

    private String textAt(Map<String, Object> node, String... paths) {
        if (node == null) {
            return null;
        }
        for (String path : paths) {
            Object current = node;
            for (String segment : path.split("\\.")) {
                current = current instanceof Map<?, ?> map ? map.get(segment) : null;
            }
            if (current != null && !current.toString().isBlank()) {
                return current.toString();
            }
        }
        return null;
    }

    private Boolean booleanAt(Map<String, Object> node, String... paths) {
        String value = textAt(node, paths);
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "success".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value) || "failed".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }
}
