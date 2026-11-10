package com.ktb.moyeota.global.websocket.subscription;

import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.security.InternalApiKeyFilter;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnServerRole(ServerRole.WEBSOCKET)
public class InternalRestClientConfig {

    @Bean
    public RestClient internalRestClient(RestClient.Builder builder, InternalApiProperties properties) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        return builder
                .baseUrl(properties.restBaseUrl())
                .defaultHeader(InternalApiKeyFilter.HEADER, properties.apiKey() == null ? "" : properties.apiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
