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
package net.tascalate.async.spring.webflux;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.tascalate.async.Scheduler;
import net.tascalate.async.spring.DefaultAsyncAwaitContextualizer;
import net.tascalate.async.spring.DefaultAsyncAwaitScheduler;

@Configuration
@ConditionalOnWebApplication(type = Type.REACTIVE)
class AsyncAwaitWebFluxConfiguration {
    
    @DefaultAsyncAwaitContextualizer
    @Bean(name = "<<default-async-await-contextualizer>>")
    @ConditionalOnMissingBean(annotation = DefaultAsyncAwaitContextualizer.class)
    AsyncAwaitContextualizer asyncAwaitContextualizer() {
        return new AsyncAwaitContextualizer();
    }
    
    @Bean(name = "<<async-await-flux-web-filter>>")
    @ConditionalOnWebApplication(type = Type.REACTIVE)
    AsyncAwaitWebFilter asyncAwaitWebFilter(@DefaultAsyncAwaitScheduler Scheduler scheduler) {
        return new AsyncAwaitWebFilter(scheduler);
    }
}
