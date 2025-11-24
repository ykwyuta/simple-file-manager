package com.example.filemanager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler)
            throws Exception {
        logger.info("START {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception ex) throws Exception {
        if (ex != null) {
            logger.error("END {} {} {} exception={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), ex.getMessage(), ex);
        } else {
            logger.info("END {} {} {}", request.getMethod(), request.getRequestURI(), response.getStatus());
        }
    }
}
