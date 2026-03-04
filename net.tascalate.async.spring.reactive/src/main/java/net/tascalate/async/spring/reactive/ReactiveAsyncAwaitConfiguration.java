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
package net.tascalate.async.spring.reactive;

import java.util.Optional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;

import net.tascalate.async.AsyncGenerator;
import net.tascalate.async.spi.CurrentCallContext;

import reactor.core.publisher.Flux;

@ConditionalOnClass({ReactiveAdapterRegistry.class, Flux.class})
@Configuration
class ReactiveAsyncAwaitConfiguration implements InitializingBean {
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    
    ReactiveAsyncAwaitConfiguration(Optional<ReactiveAdapterRegistry> reactiveAdapterRegistry) {
        this.reactiveAdapterRegistry = reactiveAdapterRegistry.orElse(ReactiveAdapterRegistry.getSharedInstance());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        reactiveAdapterRegistry.registerReactiveType(
            ReactiveTypeDescriptor.multiValue(AsyncGenerator.class, () -> AsyncGenerator.emptyOn(CurrentCallContext.scheduler())),
            asyncGenerator -> ReactorAsyncAwaitBridge.createFlux(() -> (AsyncGenerator<?>)asyncGenerator),
            publisher -> ReactorAsyncAwaitBridge.createGenerator((Flux<?>)publisher, CurrentCallContext.scheduler())
        );
    }
}
