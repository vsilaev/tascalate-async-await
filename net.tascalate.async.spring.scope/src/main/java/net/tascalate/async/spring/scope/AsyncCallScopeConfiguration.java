package net.tascalate.async.spring.scope;

import java.util.Collections;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "async-await.async-call-scope.enable", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(ProceedingJoinPoint.class)
class AsyncCallScopeConfiguration {
    @Bean(name="<<async-await-acync-call-scope-configurer>>")
    static CustomScopeConfigurer customScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.setScopes( Collections.singletonMap("async-call", AsyncExecutionScope.instance()));
        return configurer;
    }

}