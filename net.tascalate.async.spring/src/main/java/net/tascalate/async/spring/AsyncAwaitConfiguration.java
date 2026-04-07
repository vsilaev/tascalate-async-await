/**
 * Copyright 2015-2025 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.spring;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.CallContext;
import net.tascalate.async.Scheduler;
import net.tascalate.async.reactor.ReactorAsyncAwaitBridge;
import reactor.core.publisher.Flux;

@Configuration
@ComponentScan(basePackageClasses = AsyncAwaitConfiguration.class)
class AsyncAwaitConfiguration {
    
    @DefaultAsyncAwaitExecutor
    @Lazy
    @Bean(name="<<default-async-await-executor>>", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(annotation = DefaultAsyncAwaitExecutor.class)
    ExecutorService defaultAsyncAwaitExecutorService(AsyncAwaitExecutorProperties executorProperties) {
        return executorProperties.createExecutorService();
    }
    
    @DefaultAsyncAwaitScheduler
    @Bean(name="<<default-async-await-scheduler>>")
    @ConditionalOnMissingBean(annotation = DefaultAsyncAwaitScheduler.class)
    Scheduler defaultAsyncAwaitScheduler(@DefaultAsyncAwaitExecutor ExecutorService executor, 
                                         @DefaultAsyncAwaitContextualizer Optional<Function<? super Runnable, ? extends Runnable>> contextualizer,
                                         
                                         Optional<TaskSchedulerFactory> taskSchedulerFactory) {
        
        return taskSchedulerFactory.map(tsf -> tsf.create(executor, contextualizer.orElse(null)))
                                   .orElseGet(() -> Scheduler.interruptible(executor, contextualizer.orElse(null)));
    }
    
    @Configuration
    @ConditionalOnProperty(name = "async-await.async-call-scope.enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    static class AsyncCallScopeConfiguration {
        @Bean(name="<<async-await-acync-call-scope-configurer>>")
        static CustomScopeConfigurer customScopeConfigurer() {
            CustomScopeConfigurer configurer = new CustomScopeConfigurer();
            configurer.setScopes( Collections.singletonMap("async-call", AsyncExecutionScope.instance()));
            return configurer;
        }
        
        @Bean(name="<<async-await-async-call-boundary-interceptor>>")
        AsyncCallBoundaryInterceptor asyncCallBoundaryInterceptor() {
            return new AsyncCallBoundaryInterceptor();
        }

    }
    
    @Configuration
    @ConditionalOnClass({ReactiveAdapterRegistry.class, Flux.class, ReactorAsyncAwaitBridge.class})
    static class ReactiveAsyncAwaitConfiguration implements InitializingBean {
        private final ReactiveAdapterRegistry reactiveAdapterRegistry;
        
        ReactiveAsyncAwaitConfiguration(Optional<ReactiveAdapterRegistry> reactiveAdapterRegistry) {
            this.reactiveAdapterRegistry = reactiveAdapterRegistry.orElse(ReactiveAdapterRegistry.getSharedInstance());
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            reactiveAdapterRegistry.registerReactiveType(
                ReactiveTypeDescriptor.multiValue(AsyncGenerator.class, () -> AsyncGenerator.emptyOn(CallContext.scheduler())),
                asyncGenerator -> ReactorAsyncAwaitBridge.createFlux(() -> (AsyncGenerator<?>)asyncGenerator),
                publisher -> ReactorAsyncAwaitBridge.createGenerator((Flux<?>)publisher, CallContext.scheduler())
            );
        }
    }
}
