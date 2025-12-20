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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import net.tascalate.async.Scheduler;

@Configuration
@ComponentScan(basePackageClasses = AsyncAwaitConfiguration.class)
class AsyncAwaitConfiguration {
    
    @DefaultAsyncAwaitExecutor
    @Bean(name="<<default-async-await-executor>>", destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(annotation = DefaultAsyncAwaitExecutor.class)
    ExecutorService defaultAsyncAwaitExecutorService() {
        return new ThreadPoolExecutor(10, 10,
                                      1, TimeUnit.MINUTES,
                                      new LinkedBlockingDeque<>(1000),
                                      new NamedThreadFactory("async-await-scheduler-thread_"));        
    }
    
    @DefaultAsyncAwaitScheduler
    @Bean(name="<<default-async-await-scheduler>>")
    @ConditionalOnMissingBean(annotation = DefaultAsyncAwaitScheduler.class)
    Scheduler defaultAsyncAwaitScheduler(@DefaultAsyncAwaitExecutor ExecutorService executor) {
        return Scheduler.interruptible(executor);
    }
}
