package com.bankSimulate.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.bankSimulate.application.GuardCheckout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component 
public class CheckoutClosedInterceptor implements HandlerInterceptor{
    private static final Logger log = LoggerFactory.getLogger(CheckoutClosedInterceptor.class);
    private final GuardCheckout guard;

    public CheckoutClosedInterceptor(GuardCheckout guard){
        this.guard = guard;
    }

    @Override 
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception{
        Map<String , String> vars = (Map<String, String>) 
        req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(vars == null) return true;
        String id = vars.getOrDefault("tradeNo", vars.get("gwTxnId"));
        if(id == null) return true;
        Optional<String> closedAt = guard.closedAt(id);
        if(closedAt.isEmpty()) return true;

        log.warn("Blocked access to closed checkout id={} {} {} closedAt={} from={} -> 410",
                id, req.getMethod(), req.getRequestURI(), closedAt.get(), req.getRemoteAddr());

        res.setStatus(HttpServletResponse.SC_GONE);
        res.setContentType("text/html;charset=UTF-8");
        res.setHeader("Cache-Control", "no-store");
        
        res.getOutputStream().write(PAGE.getBytes(StandardCharsets.UTF_8));
        return false;    
    }
    
     private static final String PAGE = """
            <!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Phiên thanh toán đã kết thúc</title>
            <style>body{font-family:ui-sans-serif,system-ui,sans-serif;display:grid;place-items:center;
            min-height:100vh;margin:0;background:#f4f6fa;color:#1d2433}
            .box{background:#fff;border:1px solid #dde3ec;border-radius:14px;padding:28px;max-width:420px}</style>
            </head><body><div class="box">
            <h1 style="font-size:17px;margin:0 0 10px">Phiên thanh toán đã kết thúc</h1>
            <p style="margin:0;color:#5c6b80;font-size:14px">Đơn hàng này đã được xử lý. Vui lòng quay lại cửa hàng để kiểm tra trạng thái.</p>
            </div></body></html>
            """;


}
