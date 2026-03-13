package com.eliasgonzalez.cartones.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuración de concurrencia para aprovechar Virtual Threads de Java 21.
 * <p>
 * Esta configuración habilita el uso de @Async con un executor basado en virtual threads,
 * lo cual mejora significativamente el rendimiento en operaciones I/O intensivas
 * (como generación de PDFs, consultas a base de datos, etc.) sin consumir threads del sistema.
 * <p>
 * Los virtual threads son ideales para:
 * - Operaciones I/O bloqueantes (lectura/escritura de archivos, base de datos)
 * - Procesamiento concurrente de múltiples tareas independientes
 * - Reducir consumo de memoria comparado con threads tradicionales del pool
 * <p>
 * No son ideales para:
 * - Operaciones CPU-bound intensivas (cálculos matemáticos complejos)
 * - Código que usa synchronized blocks extensamente (mejor usar ReentrantLock)
 */
@Configuration
@EnableAsync
@Slf4j
public class ConcurrencyConfig implements AsyncConfigurer {

    /**
     * Configura el executor para tareas asíncronas usando Virtual Threads de Java 21.
     * <p>
     * En lugar de un ThreadPoolExecutor tradicional con tamaño fijo,
     * utilizamos Executors.newVirtualThreadPerTaskExecutor() que crea un nuevo
     * virtual thread por cada tarea asíncrona, sin límites de pool.
     *
     * @return Executor basado en virtual threads
     */
    @Override
    public Executor getAsyncExecutor() {
        log.info("Configurando executor asíncrono con Java 21 Virtual Threads");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Manejador global de excepciones no capturadas en métodos @Async.
     * <p>
     * Es crucial tener este manejador para evitar que las excepciones en tareas
     * asíncronas se pierdan silenciosamente. Registra el error con contexto completo.
     *
     * @return AsyncUncaughtExceptionHandler personalizado
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(Throwable ex, Method method, Object... params) {
                log.error("Excepción no capturada en método asíncrono: {}.{}",
                        method.getDeclaringClass().getName(),
                        method.getName(),
                        ex);
                log.error("Parámetros del método: {}", (Object) params);
                // Aquí podrías agregar notificaciones adicionales (email, Slack, etc.)
                // o almacenar el error en una tabla de auditoría
            }
        };
    }
}
