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
        registry.addInterceptor(checkoutClosed).addPathPatterns("/checkout/**", "/card-checkout/**");
    }
}
