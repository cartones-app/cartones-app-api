package com.eliasgonzalez.cartones.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        log.info("Configurando executor asíncrono con Java 21 Virtual Threads + Propagación de Seguridad");
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("virtual-async-");

        // 1. Habilitamos Virtual Threads
        executor.setVirtualThreads(true);

        // 2. Agregamos el decorador para pasar el contexto de seguridad
        executor.setTaskDecorator(new SecurityContextTaskDecorator());

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("Excepción en async: {}.{} - {}",
                    method.getDeclaringClass().getName(), method.getName(), ex.getMessage(), ex);
        };
    }

    /**
     * Decorador interno para propagar el contexto de Spring Security.
     * Captura el contexto del hilo padre y lo inyecta en el Virtual Thread.
     */
    static class SecurityContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            SecurityContext context = SecurityContextHolder.getContext();
            return () -> {
                try {
                    SecurityContextHolder.setContext(context);
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }
    }
}
