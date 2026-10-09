package com.bankSimulate.infrastructure.bank;

import com.bankSimulate.domain.cardAuth.BankFailure;
import com.bankSimulate.domain.common.ApiException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

final class BankTransportErrors {

    private BankTransportErrors() {}

    static ApiException translate(String bankCode, RestClientException ex) {

        if (timedOut(ex)) {
            return new ApiException(504, BankFailure.TIMEOUT,
                    bankCode + " không trả lời trong thời gian cho phép: " + ex.getMessage());
        }

        if (ex instanceof HttpStatusCodeException http) {
            return http.getStatusCode().value() == 503
                    ? new ApiException(502, BankFailure.UNREACHABLE, bankCode + " đang bảo trì (503)")
                    : new ApiException(502, BankFailure.CALL_REJECTED,
                            bankCode + " trả lỗi " + http.getStatusCode() + ": " + http.getMessage());
        }

        return new ApiException(502, BankFailure.UNREACHABLE,
                bankCode + " không kết nối được: " + ex.getMessage());
    }

    private static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }
}
