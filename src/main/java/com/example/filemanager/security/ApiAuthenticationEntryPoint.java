package com.example.filemanager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Returns a plain 401 without a {@code WWW-Authenticate: Basic} challenge.
 *
 * <p>
 * Omitting the challenge is deliberate: it stops browsers from showing a native
 * credential prompt and, more importantly, from caching the credentials and
 * replaying them on cross-site requests. Clients that send the Authorization
 * header preemptively (curl -u, HTTP libraries, the e2e suite) are unaffected.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":401,\"detail\":\"認証が必要です。\"}");
    }
}
