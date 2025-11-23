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

    @Pointcut("execution(* com.example.filemanager.repository..*(..))")
    public void repositoryLayer() {
    }

    @Around("serviceLayer() || repositoryLayer()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();

        logger.info("START {} args={}", methodName, Arrays.toString(args));

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            logger.info("END {} executionTime={}ms result={}", methodName, (endTime - startTime), result);
            return result;
        } catch (Throwable e) {
            long endTime = System.currentTimeMillis();
            logger.error("EXCEPTION {} executionTime={}ms exception={}", methodName, (endTime - startTime),
                    e.getMessage(), e);
            throw e;
        }
    }
}
