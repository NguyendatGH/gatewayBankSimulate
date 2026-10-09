package com.bankSimulate.domain.common;

public class ResultCode {
    public static final String FAILED = "01";
    public static final String SUCCESS = "02";
    public static final String PENDING_AUTH = "03";
    public static final String EXPIRED = "04";
    public static final String CANCELLED = "05";

    private ResultCode() {
    }

    public static boolean isFinal(String code) {
        return !PENDING_AUTH.equals(code);
    }
}
