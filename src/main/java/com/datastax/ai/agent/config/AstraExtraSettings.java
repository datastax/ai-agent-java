package com.datastax.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;

@ConfigurationProperties(prefix = "datastax.astra")
public class AstraExtraSettings {

    private File secureConnectBundle;

    /**
     * Gets secureConnectBundle
     *
     * @return value of secureConnectBundle
     */
    public File getSecureConnectBundle() {
        return secureConnectBundle;
    }

    public AstraExtraSettings setSecureConnectBundle(File secureConnectBundle) {
        this.secureConnectBundle = secureConnectBundle;
        return this;
    }
}
