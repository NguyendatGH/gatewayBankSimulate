package com.bankSimulate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bankSimulate.infrastructure.web.CheckoutClosedInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CheckoutClosedInterceptor checkoutClosed;

    public WebConfig(CheckoutClosedInterceptor checkoutClosed) {
        this.checkoutClosed = checkoutClosed;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Không đưa /gateway-return/** vào đây: đó là bước đánh dấu "đã redirect", chặn nó thì user không về được merchant.
        // Không chặn /status: tab còn mở cần hỏi được kết quả cuối để tự về trang merchant, thay vì kẹt ở trang 410.
        registry.addInterceptor(checkoutClosed).addPathPatterns("/checkout/**", "/card-checkout/**")
                .excludePathPatterns("/checkout/*/status", "/card-checkout/*/status");
    }
}
