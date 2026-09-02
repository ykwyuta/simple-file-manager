package com.example.filemanager.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(* com.example.filemanager.service..*(..))")
    public void serviceLayer() {
    }

    /**
     * Traces service calls.
     *
     * <p>
     * Arguments and return values go out at DEBUG, not INFO: they contain file
     * names, tags and user names, and emitting them for every call writes user
     * data into the application log permanently and drowns out everything else.
     * Timing stays at DEBUG too; only failures are logged unconditionally.
     *
     * <p>
     * The repository layer is deliberately not advised — Hibernate already has
     * {@code spring.jpa.show-sql} for that, and wrapping every repository call
     * doubled the log volume for no extra information.
     */
    @Around("serviceLayer()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();

        if (logger.isDebugEnabled()) {
            logger.debug("START {} args={}", methodName, Arrays.toString(joinPoint.getArgs()));
        }

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            if (logger.isDebugEnabled()) {
                logger.debug("END {} executionTime={}ms", methodName, System.currentTimeMillis() - startTime);
            }
            return result;
        } catch (Throwable e) {
            logger.error("EXCEPTION {} executionTime={}ms exception={}", methodName,
                    System.currentTimeMillis() - startTime, e.getMessage(), e);
            throw e;
        }
    }
}
