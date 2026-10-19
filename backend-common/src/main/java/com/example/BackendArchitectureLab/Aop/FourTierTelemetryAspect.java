package com.example.BackendArchitectureLab.Aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Order(1)
@Component
public class FourTierTelemetryAspect {

    private static final Logger log = LoggerFactory.getLogger(FourTierTelemetryAspect.class);

    @Pointcut("execution(* Object.*(..))")
    public void objectMethods() {
    }

    @Pointcut("execution(* com.example.BackendArchitectureLab.Controller..*.*(..))")
    public void controllerLayer() {
    }

    @Pointcut("execution(* com.example.BackendArchitectureLab.Service..*.*(..))")
    public void serviceLayer() {
    }

    @Pointcut("execution(* com.example.BackendArchitectureLab.DataAccess..*.*(..)) || execution(* com.example.BackendArchitectureLab.Dataaccess..*.*(..))")
    public void dataAccessLayer() {
    }

    @Pointcut("execution(* com.example.BackendArchitectureLab.Repository..*.*(..)) || (target(org.springframework.data.repository.Repository) && execution(* org.springframework.data.repository.Repository+.*(..)))")
    public void repositoryLayer() {
    }

    @Around("controllerLayer() && !objectMethods()")
    public Object profileController(ProceedingJoinPoint joinPoint) throws Throwable {
        return profile(joinPoint, "CONTROLLER");
    }

    @Around("serviceLayer() && !objectMethods()")
    public Object profileService(ProceedingJoinPoint joinPoint) throws Throwable {
        return profile(joinPoint, "SERVICE");
    }

    @Around("dataAccessLayer() && !objectMethods()")
    public Object profileDataAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        return profile(joinPoint, "DATAACCESS");
    }

    @Around("repositoryLayer() && !objectMethods()")
    public Object profileRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return profile(joinPoint, "REPOSITORY");
    }

    private Object profile(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        if (!CallTreeContext.isDiagnostic()) {
            return joinPoint.proceed();
        }

        String className = resolveClassName(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        InvocationNode node = null;
        try {
            node = CallTreeContext.push(layer, className, methodName);
        } catch (Throwable t) {
            log.warn("Telemetry push failed for {}.{}: {}", className, methodName, t.getMessage());
        }

        Throwable caughtError = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            caughtError = t;
            throw t;
        } finally {
            if (node != null) {
                try {
                    CallTreeContext.pop(node, caughtError == null, caughtError);
                } catch (Throwable t) {
                    log.warn("Telemetry pop failed for {}.{}: {}", className, methodName, t.getMessage());
                }
            }
        }
    }

    private String resolveClassName(ProceedingJoinPoint joinPoint) {
        Class<?> declaringType = joinPoint.getSignature().getDeclaringType();
        String className = declaringType.getSimpleName();

        if ("JpaRepository".equals(className)
                || "CrudRepository".equals(className)
                || "PagingAndSortingRepository".equals(className)
                || "Repository".equals(className)
                || "SimpleJpaRepository".equals(className)) {
            Object target = joinPoint.getTarget();
            if (target != null) {
                for (Class<?> iface : target.getClass().getInterfaces()) {
                    if (iface.getName().startsWith("com.example.BackendArchitectureLab.Repository")) {
                        return iface.getSimpleName();
                    }
                }
            }
        }
        return className;
    }
}
