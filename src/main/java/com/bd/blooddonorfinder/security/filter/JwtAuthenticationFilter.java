package com.bd.blooddonorfinder.security.filter;

import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.payload.response.ApiErrorResponse;
import com.bd.blooddonorfinder.security.UserPrincipal;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.auth.TokenStorageService;
import com.bd.blooddonorfinder.utils.constants.SecurityConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = jwtTokenProvider.resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Claims claims = jwtTokenProvider.parseAndValidateClaims(token);
                TokenType tokenType = jwtTokenProvider.getTokenType(claims);
                String jti = jwtTokenProvider.getTokenId(claims);

                if (tokenType != TokenType.ACCESS_TOKEN) {
                    log.warn("Rejected non-access token (type={}) on endpoint {}",
                            tokenType, request.getRequestURI());
                } else if (!tokenStorageService.isAccessTokenWhitelisted(jti)) {
                    log.warn("Rejected revoked access token jti={}", jti);
                } else {
                    authenticate(claims, token, request);
                }
            }
        } catch (InvalidJwtTokenException e) {
            log.warn("Rejected JWT token ({}): {}", e.getReason(), jwtTokenProvider.maskToken(token));
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, e.getReason().name(), e.getMessage());
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token received: {}", jwtTokenProvider.maskToken(token));
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_TOKEN", "Malformed or invalid JWT token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, String token, HttpServletRequest request) {
        String username = jwtTokenProvider.getUsername(claims);
        if (username == null) {
            log.warn("JWT is missing a usable username claim");
            return;
        }

        List<String> roles = jwtTokenProvider.getRoles(claims);
        UUID userId = jwtTokenProvider.getUserId(claims);
        Set<GrantedAuthority> grantedAuthorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        UserPrincipal principal = new UserPrincipal(
                userId,
                username,
                "",
                true,
                grantedAuthorities
        );

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user '{}' with masked token: {}",
                username, jwtTokenProvider.maskToken(token));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // getServletPath() excludes the configured context path, unlike getRequestURI().
        return request.getServletPath().startsWith("/api/auth/");
    }

    private void sendJsonError(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(status);
        ApiErrorResponse errorResponse = new ApiErrorResponse();
        errorResponse.setMessage(message);
        errorResponse.setStatus(status);
        errorResponse.setError(code);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
