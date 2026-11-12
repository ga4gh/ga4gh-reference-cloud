package org.ga4gh.refcloud.api.compute;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// run preparation (cloning workflows, downloading DRS inputs) runs on its own pool, so slow downloads never hold up the
// scheduled jobs (health checks, the TES reconciler, the run poller)
@Configuration
@EnableAsync
public class ComputeAsyncConfig {

    public static final String PREPARATION_EXECUTOR = "computePreparationExecutor";

    @Bean(name = PREPARATION_EXECUTOR)
    public ThreadPoolTaskExecutor computePreparationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("compute-prepare-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
