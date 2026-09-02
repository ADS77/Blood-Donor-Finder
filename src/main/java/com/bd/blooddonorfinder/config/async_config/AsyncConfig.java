package com.bd.blooddonorfinder.config.async_config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties({
        AsyncConfig.AuditExecutorProperties.class,
        AsyncConfig.GeoFetchExecutorProperties.class
})
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @ConfigurationProperties("sondhan.async.audit")
    public record AuditExecutorProperties(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int keepAliveSeconds) {
    }

    @ConfigurationProperties("sondhan.async.geo-fetch")
    public record GeoFetchExecutorProperties(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int keepAliveSeconds) {
    }

    private ThreadPoolTaskExecutor buildExecutor(
            String threadNamePrefix,
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int keepAliveSeconds) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Wait for in-flight tasks to complete before shutdown, bounded by awaitTerminationSeconds.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("{} initialized — core={}, max={}, queue={}, keepAliveSeconds={}",
                threadNamePrefix, corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);

        return executor;
    }


    @Bean(name = "auditExecutor")
    public Executor auditExecutor(AuditExecutorProperties props) {
        return buildExecutor(
                "AuditExecutor-",
                props.corePoolSize(),
                props.maxPoolSize(),
                props.queueCapacity(),
                props.keepAliveSeconds());
    }

    @Bean(name = "geoFetchExecutor")
    public Executor geoFetchExecutor(GeoFetchExecutorProperties props) {
        return buildExecutor(
                "GeoFetchExecutor-",
                props.corePoolSize(),
                props.maxPoolSize(),
                props.queueCapacity(),
                props.keepAliveSeconds());
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("UnnamedAsyncExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.warn("Default (unnamed) async executor initialized — verify no @Async call site " +
                "is missing an explicit executor bean name.");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Uncaught exception in async method {}: {}",
                        method.getName(), throwable.getMessage(), throwable);
    }
}

