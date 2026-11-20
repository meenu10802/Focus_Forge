package com.focusforge.ai.provider;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class AiClientConfig {

    /** Applies connect/read timeouts to every RestClient.Builder the providers receive. */
    @Bean
    RestClientCustomizer aiTimeouts(AiProperties props) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
    }

    /** The provider selected by {@code focus.ai.provider}. Fails fast (at startup) on an unknown name. */
    public static AiProvider select(List<AiProvider> providers, String name) {
        Map<String, AiProvider> byName = providers.stream()
                .collect(Collectors.toMap(AiProvider::name, Function.identity()));
        AiProvider provider = byName.get(name);
        if (provider == null) {
            throw new IllegalStateException("Unknown focus.ai.provider '" + name
                    + "'. Available: " + byName.keySet());
        }
        return provider;
    }
}
