package com.example.BackendArchitectureLab.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtAuthenticationToken jwtAuthenticationToken;
    private final ObjectMapper objectMapper;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtAuthenticationToken jwtAuthenticationToken,
            ObjectMapper objectMapper,
            UserDetailsService userDetailsService) {
        this.jwtAuthenticationToken = jwtAuthenticationToken;
        this.objectMapper = objectMapper;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/api-docs")
            || path.startsWith("/swagger-resources")
            || path.startsWith("/webjars")
            || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = resolveToken(request);
            if (token != null && !token.isBlank()) {
                token = token.replaceFirst("^Bearer\\s+", "").trim();
                JwtClaims claims = jwtAuthenticationToken.verifyJWT(token);
                String email = (String) claims.getClaimValue("email");

                    if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails;
                    if (userDetailsService != null) {
                        userDetails = userDetailsService.loadUserByUsername(email);
                    } else {
                        userDetails = new org.springframework.security.core.userdetails.User(email, "", List.of());
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (InvalidJwtException e) {
            handleErrorResponse(response, HttpStatus.UNAUTHORIZED, "AUTH_ERROR", "Unauthorized");
            return;
        } catch (Exception e) {
            log.error("JWT authentication filter error", e);
            handleErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void handleErrorResponse(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        ResponseType<Object> failResponse = ResponseType.Fail(code, message, status.value());
        response.getWriter().write(objectMapper.writeValueAsString(failResponse));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && !header.isBlank()) {
            return header;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && Objects.equals(cookie.getName(), "v3-admin-vite-token-key")) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
