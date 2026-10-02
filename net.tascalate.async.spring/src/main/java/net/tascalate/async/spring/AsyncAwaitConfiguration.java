/**
 * Copyright 2015-2026 Valery Silaev (http://vsilaev.com)
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

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.CallContext;
import net.tascalate.async.Scheduler;
import net.tascalate.async.reactor.ReactorAsyncAwaitBridge;
import net.tascalate.async.spring.concurrent.AsyncAwaitExecutorProperties;
import net.tascalate.async.spring.concurrent.TaskSchedulerFactory;
import net.tascalate.async.spring.util.AbstractSmartLifecycle;
import reactor.core.publisher.Flux;


@Configuration
@ComponentScan(basePackages = {
    "net.tascalate.async.spring",
    "net.tascalate.async.mutiny.spring",
    "net.tascalate.async.reactor.spring"
})
class AsyncAwaitConfiguration {

    @DefaultAsyncAwaitExecutor
    @Lazy
    @Bean(name="<<default-async-await-executor>>", destroyMethod = "shutdown")
    @Conditional(ExecutorConditions.UsePlatformThreads.class)
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
    @ConditionalOnClass({ReactiveAdapterRegistry.class, Flux.class, ReactorAsyncAwaitBridge.class})
    static class ReactorAsyncAwaitConfiguration {

        @Bean(name = "<<async-await-reactive-types-registar>>")
        SmartLifecycle asyncAwaitReactiveTypesRegistar(Optional<ReactiveAdapterRegistry> reactiveAdapterRegistry) {
            ReactiveAdapterRegistry actualReactiveAdapterRegistry =
                reactiveAdapterRegistry.orElseGet(() ->ReactiveAdapterRegistry.getSharedInstance());

            return new AbstractSmartLifecycle() {
                @Override
                public void start() {
                    actualReactiveAdapterRegistry.registerReactiveType(
                        ReactiveTypeDescriptor.multiValue(AsyncGenerator.class, () -> AsyncGenerator.emptyOn(CallContext.scheduler())),
                        asyncGenerator -> ReactorAsyncAwaitBridge.createFlux(() -> (AsyncGenerator<?>)asyncGenerator),
                        publisher -> ReactorAsyncAwaitBridge.createGenerator((Flux<?>)publisher, CallContext.scheduler())
                    );
                    super.start();
                }
            };
        }
    }
    
    @Configuration
    @ConditionalOnProperty(name = "async-await.async-call-scope.enable", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({Aspect.class, AopUtils.class})
    static class AsyncCallScopeConfiguration {

        @Bean(name="<<default-async-call-boundary-interceptor>>")
        @ConditionalOnMissingBean(DefaultAsyncCallBoundaryInterceptor.class)
        @ConditionalOnProperty(prefix = "spring.aop", name = "auto", havingValue = "true", matchIfMissing = true)
        DefaultAsyncCallBoundaryInterceptor asyncCallBoundaryInterceptor() {
            return new DefaultAsyncCallBoundaryInterceptor();
        }

    }
    
    /*
        
        @Bean(name="<<reactor-async-call-boundary-interceptor>>")
        ReactorAsyncCallBoundaryInterceptor reactorCallBoundaryInterceptor() {
            return Aspects.aspectOf(ReactorAsyncCallBoundaryInterceptor.class);
        }
     */
    
    /*
    @Configuration
    @ConditionalOnClass({Multi.class, MutinyAsyncAwaitBridge.class})
    static class MutinyAsyncAwaitConfiguration {
        @Bean(name="<<mutiny-async-call-boundary-interceptor>>")
        MutinyAsyncCallBoundaryInterceptor mutinyCallBoundaryInterceptor() {
            return Aspects.aspectOf(MutinyAsyncCallBoundaryInterceptor.class);
        }
    }
    */
}
