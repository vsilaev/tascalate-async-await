package net.tascalate.async.spring.webflux;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ReactiveTypeDescriptor;

import net.tascalate.async.AsyncGenerator;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
class AsyncAwaitReactiveTypesConfiguration implements InitializingBean {
    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    
    AsyncAwaitReactiveTypesConfiguration(ReactiveAdapterRegistry reactiveAdapterRegistry) {
        this.reactiveAdapterRegistry = reactiveAdapterRegistry;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        reactiveAdapterRegistry.registerReactiveType(
            ReactiveTypeDescriptor.multiValue(AsyncGenerator.class, AsyncGenerator::empty),
            asyncGenerator -> AsyncAwaitFlux.create(() -> (AsyncGenerator<?>)asyncGenerator),
            publisher -> {
                System.out.println(publisher);
                return null;
            }
        );
    }
}
