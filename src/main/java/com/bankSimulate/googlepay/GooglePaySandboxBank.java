package com.bankSimulate.googlepay;

import com.bankSimulate.domain.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * "Ngân hàng" mô phỏng cho Google Pay. Token Google Pay TEST (gateway "example") chỉ là dữ liệu mẫu, nên KHÔNG giải mã
 * và KHÔNG coi việc có token là bằng chứng ngân hàng duyệt. Kết quả đến từ kịch bản:
 * trang checkout chọn kịch bản khi {@code sandbox-scenarios-enabled=true}, còn tắt thì luôn dùng {@code default-scenario}.
 */
@Component
public class GooglePaySandboxBank {

    public enum Scenario { APPROVED, DECLINED, BANK_TIMEOUT }

    private final boolean enabled;
    private final String environment;
    private final boolean scenariosEnabled;
    private final Scenario defaultScenario;

    public GooglePaySandboxBank(@Value("${gateway.google-pay.enabled:true}") boolean enabled,
                                @Value("${gateway.google-pay.environment:TEST}") String environment,
                                @Value("${gateway.google-pay.sandbox-scenarios-enabled:true}") boolean scenariosEnabled,
                                @Value("${gateway.google-pay.default-scenario:APPROVED}") Scenario defaultScenario) {
        if (!"TEST".equals(environment))
            throw new IllegalStateException("gateway.google-pay.environment chỉ hỗ trợ TEST ở phase này, nhận: " + environment);
        this.enabled = enabled;
        this.environment = environment;
        this.scenariosEnabled = scenariosEnabled;
        this.defaultScenario = defaultScenario;
    }

    public boolean enabled() {
        return enabled;
    }

    public String environment() {
        return environment;
    }

    public boolean scenariosEnabled() {
        return scenariosEnabled;
    }

    /** Tắt điều khiển kịch bản thì bỏ qua mọi giá trị client gửi lên. */
    public Scenario resolve(String requested) {
        if (!scenariosEnabled || requested == null || requested.isBlank()) return defaultScenario;
        try {
            return Scenario.valueOf(requested.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "GOOGLE_PAY_SCENARIO_INVALID", "Unknown sandbox scenario");
        }
    }
}
