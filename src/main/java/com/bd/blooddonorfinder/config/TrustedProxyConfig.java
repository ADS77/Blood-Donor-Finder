package com.bd.blooddonorfinder.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrustedProxyConfig {
    private static final String DEFAULT_PROXY_REGEX =
            "10\\.\\d+\\.\\d+\\.\\d+|"
                    + "172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+|"
                    + "192\\.168\\.\\d+\\.\\d+|"
                    + "103\\.(21|22|31)\\.(2[24-9]|3[0-1]|1[6-9])\\.\\d+|"
                    + "104\\.(16|17|18|19|2[0-6])\\.\\d+\\.\\d+|"
                    + "108\\.162\\.(19[2-9]|2[0-2]\\d)\\.\\d+|"
                    + "::1|fd[0-9a-f]{2}:.*";
    private final String trustedProxyRegex;

    public TrustedProxyConfig(
            @Value("${app.proxy.trusted-ip-regex:" + DEFAULT_PROXY_REGEX + "}") String trustedProxyRegex) {
        this.trustedProxyRegex = trustedProxyRegex;
    }

    /**
     * RemoteIpValve only honours X-Forwarded-* headers when the direct peer is
     * in the trusted-proxy allow-list. A standalone ForwardedHeaderFilter would
     * trust those headers from any client and allow remote-address spoofing.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> remoteIpValveCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            var valve = new org.apache.catalina.valves.RemoteIpValve();
            valve.setTrustedProxies(trustedProxyRegex);
            valve.setRemoteIpHeader("X-Forwarded-For");
            valve.setProtocolHeader("X-Forwarded-Proto");
            valve.setProtocolHeaderHttpsValue("https");
            context.getPipeline().addValve(valve);
        });
    }
}
