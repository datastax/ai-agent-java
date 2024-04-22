package com.datastax.ai.agent.config;

import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class AstraConfiguration {

    @Bean
    public CqlSessionBuilderCustomizer sessionBuilderCustomizer(AstraExtraSettings astraSettings) {
        Path bundle = astraSettings.getSecureConnectBundle().toPath();
        return builder -> builder.withCloudSecureConnectBundle(bundle);
    }

}
