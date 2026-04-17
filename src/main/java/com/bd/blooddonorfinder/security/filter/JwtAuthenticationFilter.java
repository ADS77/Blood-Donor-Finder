package com.bd.blooddonorfinder.security.filter;

import com.bd.blooddonorfinder.model.enums.TokenType;
import com.bd.blooddonorfinder.security.JwtUserDetails;
import com.bd.blooddonorfinder.security.exception.InvalidJwtTokenException;
import com.bd.blooddonorfinder.security.jwt.JwtTokenProvider;
import com.bd.blooddonorfinder.service.auth.TokenStorageService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStorageService tokenStorageService;
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = jwtTokenProvider.resolveToken(request);

            if(token != null && SecurityContextHolder.getContext().getAuthentication() == null){
                Claims claims = jwtTokenProvider.parseAndValidate(token);

                // Only access tokens are valid for API authorization
                TokenType tokenType = jwtTokenProvider.getTokenType(claims);
                if(tokenType != TokenType.ACCESS_TOKEN){
                    log.warn("Rejected non-access token (type={}) on endpoint {}",
                            tokenType, request.getRequestURI());
                    filterChain.doFilter(request, response);
                    return;
                }
                // Verify token hasn't been revoked
                String jti = jwtTokenProvider.getTokenId(claims);
                if (!tokenStorageService.isAccessTokenWhitelisted(jti)) {
                    log.warn("Rejected revoked access token jti={}", jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtTokenProvider.getUsername(claims);
                List<String> roles = jwtTokenProvider.getRoles(claims);
                Long userId = claims.get("uid", Long.class);
                JwtUserDetails userDetails = new JwtUserDetails(userId, username, roles);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user={} with roles={}", username, roles);
            }
        }catch (InvalidJwtTokenException e) {
            log.debug("Token validation failed: {} (reason={})", e.getMessage(), e.getReason());
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/");
    }


}
