package com.bankSimulate.domain.enums;

public enum ThreeDsPreference {
    NO_PREFERENCE,
    REQUEST_CHALLENGE,
    REQUEST_NO_CHALLENGE;

    public static ThreeDsPreference from(ThreeDsPolicy policy) {
        if (policy == null) return NO_PREFERENCE;
        return switch (policy) {
            case REQUIRED -> REQUEST_CHALLENGE;
            case DISABLED -> REQUEST_NO_CHALLENGE;
            case OPTIONAL -> NO_PREFERENCE;
        };
    }
}
