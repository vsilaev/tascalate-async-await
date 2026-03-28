package net.tascalate.async.spring.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

import net.tascalate.async.spi.PerMethodSchedulerResolver;

@Configuration
class AsyncOnConfiguration {
    
    @DefaultAsyncOnSchedulerResolver
    @Bean(name = "<<async-on-scheduler-resolver>>")
    @ConditionalOnMissingBean(annotation = DefaultAsyncOnSchedulerResolver.class)
    PerMethodSchedulerResolver<?, ?> perMethodSchedulerResolver(ApplicationContext ctx) {
        return new AsyncOnSchedulerResolver(ctx);
    }
    
    @Bean(name = "<<async-on-scheduler-resolver-installer>>")
    ApplicationListener<ContextRefreshedEvent> onContextRefreshed(@DefaultAsyncOnSchedulerResolver PerMethodSchedulerResolver<?, ?> schedulerResolver) {
        return __ -> VMDefaultSchedulerResolver.install(schedulerResolver);
    }

}
