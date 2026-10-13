package com.bankSimulate.infrastructure.bank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AcquirerEndpoints {

    private final Environment env;
    private final String defaultBaseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public AcquirerEndpoints(Environment env,
                             @Value("${gateway.mock-bank.base-url:http://localhost:8090}") String defaultBaseUrl,
                             @Value("${gateway.mock-bank.connect-timeout:PT3S}") Duration connectTimeout,
                             @Value("${gateway.mock-bank.read-timeout:PT20S}") Duration readTimeout) {
        this.env = env;
        this.defaultBaseUrl = defaultBaseUrl;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    public RestClient client(String acquirerCode) {
        String baseUrl = env.getProperty("gateway.mock-bank." + acquirerCode + ".base-url", defaultBaseUrl)
                .replaceAll("/$", "");
        return clients.computeIfAbsent(baseUrl, this::build);
    }

    private RestClient build(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
