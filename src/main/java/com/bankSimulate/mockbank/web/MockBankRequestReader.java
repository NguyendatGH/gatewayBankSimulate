package com.bankSimulate.mockbank.web;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.logging.GatewayLogContext;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mock bank nhận body dạng String để kiểm chữ ký trên đúng chuỗi Gateway đã ký.
 * Vì không còn @Valid @RequestBody DTO, class này parse + validate thủ công SAU khi chữ ký hợp lệ.
 */
@Component
class MockBankRequestReader {

    private final ObjectMapper json;
    private final Validator validator;

    MockBankRequestReader(ObjectMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    /** Body chưa verify: chỉ đọc MerNo/TradeNo để gắn prefix log, không dùng để xử lý. */
    void tagLogFromUntrusted(String rawBody, String merchantField, String tradeField) {
        try {
            JsonNode n = json.readTree(rawBody);
            GatewayLogContext.setMerchantNo(n.path(merchantField).asText(null));
            GatewayLogContext.setTradeNo(n.path(tradeField).asText(null));
        } catch (RuntimeException ignored) {
            // body hỏng: để bước kiểm chữ ký từ chối
        }
    }

    /** Gọi sau khi chữ ký hợp lệ; lỗi dữ liệu trả 400 giống @Valid trước đây. */
    <T> T read(String rawBody, Class<T> type) {
        T value;
        try {
            value = json.readValue(rawBody, type);
        } catch (RuntimeException ex) {
            throw new ApiException(400, "INVALID_REQUEST", "Request is invalid");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty())
            throw new ConstraintViolationException(violations);
        return value;
    }
}
