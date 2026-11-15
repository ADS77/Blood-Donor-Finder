package com.bd.blooddonorfinder.config.security_config;

import com.bd.blooddonorfinder.config.cors_config.CorsProperties;
import com.bd.blooddonorfinder.security.RestAccessDeniedHandler;
import com.bd.blooddonorfinder.security.RestAuthEntryPoint;
import com.bd.blooddonorfinder.security.filter.IpRateLimitFilter;
import com.bd.blooddonorfinder.security.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String[] PUBLIC_ENDPOINTS_WITH_API_DOCS = {
            "/api/auth/**",
            "/api/public/**",
            "/api/search/donors",
            "/api/search/donors/nearby",
            // WebSocket handshake is public at the HTTP layer; STOMP CONNECT
            // frames are authenticated by StompAuthChannelInterceptor.
            "/ws/**",
            "/ws-sockjs/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            "/error"
    };

    private static final String[] PUBLIC_ENDPOINTS_NO_API_DOCS = {
            "/api/auth/**",
            "/api/public/**",
            "/api/search/donors",
            "/api/search/donors/nearby",
            "/ws/**",
            "/ws-sockjs/**",
            "/actuator/health/**",
            "/error"
    };

    @Value("${app.security.api-docs.public:false}")
    private boolean apiDocsPublic;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.security.rate-limit.auth-per-minute:30}")
    private int authRateLimitPerMinute;

    @Value("${app.security.rate-limit.search-per-minute:60}")
    private int searchRateLimitPerMinute;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthEntryPoint authEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          CorsProperties corsProperties,
                          StringRedisTemplate redisTemplate) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Stateless bearer-token API: there is no browser-managed session
                // cookie for an attacker to ride, so CSRF protection is not needed.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(apiDocsPublic ? PUBLIC_ENDPOINTS_WITH_API_DOCS : PUBLIC_ENDPOINTS_NO_API_DOCS)
                        .permitAll()
                        .requestMatchers("/actuator/**").hasAnyRole("SYSTEM_ADMIN", "SUPER_ADMIN", "ORG_ADMIN")
                        .requestMatchers("/api/mail/**", "/es/**").hasAnyRole("SYSTEM_ADMIN", "SUPER_ADMIN", "ORG_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(ipRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(opts -> {})
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                );
        return http.build();
    }

    @Bean
    public IpRateLimitFilter ipRateLimitFilter() {
        // registered here (not as a @Component) so it only runs once, inside the security chain
        return new IpRateLimitFilter(redisTemplate, rateLimitEnabled, authRateLimitPerMinute, searchRateLimitPerMinute);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("At least one CORS origin must be configured");
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "Wildcard CORS origin is not allowed when credentials are enabled");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
